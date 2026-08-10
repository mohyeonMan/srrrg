package link.srrrg.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import link.srrrg.auth.WebSessionService;
import link.srrrg.campaign.importing.CampaignImportWorker;
import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthIdentityService;
import link.srrrg.identity.OAuthIdentityService.LoginResolution;
import link.srrrg.identity.OAuthProvider;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.ApiKeyService;
import link.srrrg.project.InvitationEmailSender;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRole;
import link.srrrg.project.ProjectService;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CampaignPostgreSqlIntegrationTest {

	private static final String JWT_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
	// 로컬 공유 Redis에 이전 테스트 실행의 rate limit 카운터가 남아 있어도 겹치지 않게 실행마다 새 네임스페이스를 쓴다.
	private static final String RATE_LIMIT_PREFIX = "srrrg:test:" + java.util.UUID.randomUUID() + ":";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("srrrg.base-url", () -> "https://srrrg.link");
		registry.add("srrrg.auth.jwt.active-kid", () -> "test");
		registry.add("srrrg.auth.jwt.active-key-base64", () -> JWT_KEY);
		registry.add("srrrg.oauth.google.client-id", () -> "google-client");
		registry.add("srrrg.oauth.google.client-secret", () -> "google-secret");
		registry.add("srrrg.oauth.kakao.client-id", () -> "kakao-client");
		registry.add("srrrg.oauth.kakao.client-secret", () -> "kakao-secret");
		registry.add("srrrg.oauth.github.client-id", () -> "github-client");
		registry.add("srrrg.oauth.github.client-secret", () -> "github-secret");
		registry.add("srrrg.url-risk.provider", () -> "fixed-safe");
		registry.add("srrrg.rate-limit.key-prefix", () -> RATE_LIMIT_PREFIX);
		// 실제 @Scheduled worker가 이 테스트의 수동 importWorker.tick() 호출과 동시에 같은 행을 다투지 않게 한다.
		registry.add("srrrg.csv-worker.initial-delay-ms", () -> "3600000");
		registry.add("srrrg.csv-worker.fixed-delay-ms", () -> "3600000");
	}

	@Autowired OAuthIdentityService identityService;
	@Autowired WebSessionService sessionService;
	@Autowired ProjectMemberRepository projectMemberRepository;
	@Autowired ProjectService projectService;
	@Autowired ApiKeyService apiKeyService;
	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired CampaignImportWorker importWorker;

	@MockitoBean
	InvitationEmailSender invitationEmailSender;

	private static int counter = 0;

	@Test
	void campaignDefaultDestinationIsResolvedDynamicallyAndMissingDestinationReturnsGone() throws Exception {
		Owner owner = newOwner();
		MvcResult createdCampaign = mockMvc.perform(post("/api/web/projects/{projectId}/campaigns", owner.projectId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"동적 목적지\",\"defaultOriginalUrl\":\"https://first.example/path\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.defaultOriginalUrl").value("https://first.example/path"))
				.andReturn();
		Long campaignId = Long.valueOf(readJson(createdCampaign, "id"));

		MvcResult createdLink = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalUrl").doesNotExist())
				.andReturn();
		String code = readJson(createdLink, "code");

		mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://first.example/path"));

		mockMvc.perform(patch("/api/web/campaigns/{id}", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"defaultOriginalUrl\":\"https://second.example/path\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://second.example/path"));

		mockMvc.perform(patch("/api/web/campaigns/{id}", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"defaultOriginalUrl\":null}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isGone());
	}

	@Test
	void campaignWithUtmTemplateCanBeReadAfterServiceTransactionEnds() throws Exception {
		Owner owner = newOwner();
		Long templateId = createTemplate(owner, "utm_source");
		Long campaignId = createCampaign(owner, "템플릿 조회 캠페인", templateId);

		mockMvc.perform(get("/api/web/projects/{projectId}/campaigns", owner.projectId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].utmTemplateId").value(templateId))
				.andExpect(jsonPath("$.items[0].utmTemplateName").isNotEmpty());

		mockMvc.perform(get("/api/web/campaigns/{campaignId}", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.utmTemplateId").value(templateId))
				.andExpect(jsonPath("$.utmTemplateName").isNotEmpty());

		mockMvc.perform(get("/api/web/projects/{projectId}/overview", owner.projectId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.campaigns[0].utmTemplateId").value(templateId))
				.andExpect(jsonPath("$.campaigns[0].utmTemplateName").isNotEmpty());
	}

	@Test
	void createsCampaignLinkMergingUtmAndPreservingExistingQueryOnRedirect() throws Exception {
		Owner owner = newOwner();

		Long templateId = createTemplate(owner, "utm_source");
		Long campaignId = createCampaign(owner, "봄 캠페인", templateId);

		MvcResult created = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/path?lang=ko\",\"externalId\":\"user-1\",\"utmValues\":{\"utm_source\":\"newsletter\"}}"))
				.andExpect(status().isCreated())
				.andReturn();
		String code = readJson(created, "code");

		MvcResult redirect = mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isFound())
				.andReturn();
		String location = redirect.getResponse().getHeader("Location");
		assertThat(location).contains("lang=ko").contains("utm_source=newsletter");
	}

	@Test
	void omittedUtmValueDynamicallyUsesCurrentCampaignDefaultWithoutCopyingItToLink() throws Exception {
		Owner owner = newOwner();
		Long templateId = createTemplate(owner, "utm_source");
		Long campaignId = createCampaign(owner, "동적 UTM 캠페인", templateId);
		updateUtmDefault(owner, campaignId, "first-source");

		MvcResult created = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/dynamic\"}"))
				.andExpect(status().isCreated()).andReturn();
		String code = readJson(created, "code");
		Long linkId = jdbcTemplate.queryForObject("SELECT id FROM links WHERE code=?", Long.class, code);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM link_utm_values WHERE link_id=?", Long.class, linkId)).isZero();
		MvcResult explicitCreated = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/explicit\",\"utmValues\":{\"utm_source\":\"fixed-source\"}}"))
				.andExpect(status().isCreated()).andReturn();
		String explicitCode = readJson(explicitCreated, "code");

		mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.containsString("utm_source=first-source")));
		updateUtmDefault(owner, campaignId, "second-source");
		mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.containsString("utm_source=second-source")));
		mockMvc.perform(get("/{code}", explicitCode).header("Host", owner.host))
				.andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.containsString("utm_source=fixed-source")));

		mockMvc.perform(get("/api/web/campaigns/{id}/statistics", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.utm[?(@.value == 'first-source')].entries")
						.value(org.hamcrest.Matchers.contains(1)))
				.andExpect(jsonPath("$.utm[?(@.value == 'second-source')].entries")
						.value(org.hamcrest.Matchers.contains(1)))
				.andExpect(jsonPath("$.utm[?(@.value == 'fixed-source')].entries")
						.value(org.hamcrest.Matchers.contains(1)));
	}

	@Test
	void utmStatisticsFollowCurrentFieldsAndRestoreSameNameHistory() throws Exception {
		Owner owner = newOwner();
		Long templateId = createTemplate(owner, "utm_source");
		Long fieldId = jdbcTemplate.queryForObject(
				"SELECT id FROM utm_template_fields WHERE utm_template_id=? AND name='utm_source' AND deleted_at IS NULL",
				Long.class, templateId);
		Long campaignId = createCampaign(owner, "필드 변경 캠페인", templateId);
		MvcResult created = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/stats\",\"utmValues\":{\"utm_source\":\"newsletter\"}}"))
				.andExpect(status().isCreated()).andReturn();
		String code = readJson(created, "code");
		mockMvc.perform(get("/{code}", code).header("Host", owner.host)).andExpect(status().isFound());

		addField(owner, templateId, "utm_medium").andExpect(status().isCreated());
		mockMvc.perform(get("/api/web/campaigns/{id}/statistics", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.utm[?(@.field == 'utm_medium' && @.value == '(없음)')].entries")
						.value(org.hamcrest.Matchers.contains(1)));

		mockMvc.perform(delete("/api/web/projects/{projectId}/utm-templates/{templateId}/fields/{fieldId}",
				owner.projectId, templateId, fieldId).with(csrf()).cookie(owner.cookie)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/web/campaigns/{id}/statistics", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk()).andExpect(jsonPath("$.utm[?(@.field == 'utm_source')]").isEmpty());

		addField(owner, templateId, "utm_source").andExpect(status().isCreated());
		mockMvc.perform(get("/api/web/campaigns/{id}/statistics", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.utm[?(@.field == 'utm_source' && @.value == 'newsletter')].entries")
						.value(org.hamcrest.Matchers.contains(1)));
	}

	@Test
	void campaignStatisticsReconcileAndEachCampaignLinkHasStandaloneDrillDown() throws Exception {
		Owner owner = newOwner();
		Long templateId = createTemplate(owner, "utm_source");
		Long campaignId = createCampaign(owner, "통계 캠페인", templateId);
		MvcResult created = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/stats\",\"externalId\":\"person-1\",\"utmValues\":{\"utm_source\":\"newsletter\"}}"))
				.andExpect(status().isCreated()).andReturn();
		String code = readJson(created, "code");
		mockMvc.perform(get("/{code}", code).header("Host", owner.host).header("User-Agent", "Mozilla/5.0 Chrome/120"))
				.andExpect(status().isFound());

		mockMvc.perform(get("/api/web/campaigns/{id}/statistics", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.entries").value(1))
				.andExpect(jsonPath("$.summary.checkedLinks").value(1))
				.andExpect(jsonPath("$.links[0].code").value(code))
				.andExpect(jsonPath("$.utm[0].field").value("utm_source"))
				.andExpect(jsonPath("$.utm[0].entries").value(1));
		mockMvc.perform(get("/api/web/projects/{projectId}/links/{code}/statistics", owner.projectId, code).cookie(owner.cookie))
				.andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("LINK"))
				.andExpect(jsonPath("$.summary.entries").value(1));
		mockMvc.perform(get("/api/web/projects/{projectId}/statistics", owner.projectId).cookie(owner.cookie))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.entries").value(1))
				.andExpect(jsonPath("$.campaigns[0].entries").value(1));

		ApiKeyService.CreatedKey statsKey = apiKeyService.create(owner.userId, owner.projectId, "stats",
				java.util.Set.of(ApiKeyScope.STATS_READ), null);
		mockMvc.perform(get("/api/v1/projects/{projectId}/links/{code}/statistics", owner.projectId, code)
					.header("Authorization", "Bearer " + statsKey.rawKey()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.entries").value(1));

		mockMvc.perform(delete("/api/web/campaigns/{id}", campaignId).with(csrf()).cookie(owner.cookie))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/web/campaigns/{id}/statistics", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.entries").value(0));
	}

	@Test
	void statisticsRangeQueryUsesTheTimeAndLinkIndexAtOneHundredThousandEvents() throws Exception {
		Owner owner = newOwner();
		MvcResult created = mockMvc.perform(post("/api/web/projects/{projectId}/links", owner.projectId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/performance\"}"))
				.andExpect(status().isCreated()).andReturn();
		Long linkId = jdbcTemplate.queryForObject("SELECT id FROM links WHERE code=?", Long.class, readJson(created, "code"));
		jdbcTemplate.update("""
				INSERT INTO link_access_events(link_id,accessed_at,outcome,is_bot)
				SELECT ?,now()-(n||' minutes')::interval,'REDIRECTED',false FROM generate_series(1,100000) n
				""", linkId);
		String plan = String.join("\n", jdbcTemplate.queryForList("""
				EXPLAIN (ANALYZE, BUFFERS)
				SELECT COUNT(e.id) FROM links l JOIN link_access_events e ON e.link_id=l.id
				WHERE e.accessed_at>=now()-interval '7 days' AND e.accessed_at<now() AND l.project_id=?
				""", String.class, owner.projectId));
		assertThat(plan).contains("idx_link_access_events_link_time", "Execution Time");
	}

	@Test
	void rejectsDuplicateExternalIdWithinSameCampaign() throws Exception {
		Owner owner = newOwner();
		Long campaignId = createCampaign(owner, "중복 방지 캠페인", null);

		mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/a\",\"externalId\":\"dup-1\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/b\",\"externalId\":\"dup-1\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXTERNAL_ID_CONFLICT"));
	}

	@Test
	void viewerCannotCreateCampaignOrLink() throws Exception {
		Owner owner = newOwner();
		LoginResolution viewerLogin = identityService.resolve(identity("viewer-" + (++counter), "viewer" + counter + "@example.com"));
		jdbcTemplate.update("INSERT INTO project_members (project_id, user_id, role, created_at) VALUES (?, ?, 'VIEWER', now())",
				owner.projectId, viewerLogin.user().getId());
		Cookie viewerCookie = new Cookie("srrrg_access", sessionService.issue(viewerLogin.user()).accessToken());

		mockMvc.perform(post("/api/web/projects/{projectId}/campaigns", owner.projectId)
					.with(csrf()).cookie(viewerCookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"뷰어 캠페인\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void archivingCampaignSoftDeletesLinksReturningGoneOnRedirect() throws Exception {
		Owner owner = newOwner();
		Long campaignId = createCampaign(owner, "삭제될 캠페인", null);
		MvcResult created = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/gone\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		String code = readJson(created, "code");

		mockMvc.perform(delete("/api/web/campaigns/{id}", campaignId).with(csrf()).cookie(owner.cookie))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isGone());
	}

	@Test
	void editorCanManageAndDeleteSingleCampaignLinkFromSharedDetailPage() throws Exception {
		Owner owner = newOwner();
		Long campaignId = createCampaign(owner, "개별 삭제 캠페인", null);
		MvcResult created = mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/delete-me\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		String code = readJson(created, "code");

		mockMvc.perform(get("/api/web/projects/{projectId}/links/{code}", owner.projectId, code).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.shortUrl").value("https://" + owner.host + "/" + code))
				.andExpect(jsonPath("$.campaignId").value(campaignId))
				.andExpect(jsonPath("$.editable").value(true));
		mockMvc.perform(patch("/api/web/projects/{projectId}/links/{code}", owner.projectId, code)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/individual\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.originalUrl").value("https://example.com/individual"));
		mockMvc.perform(patch("/api/web/projects/{projectId}/links/{code}", owner.projectId, code)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":null}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.originalUrl").doesNotExist());

		mockMvc.perform(delete("/api/web/projects/{projectId}/links/{code}", owner.projectId, code)
					.with(csrf()).cookie(owner.cookie))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/{code}", code).header("Host", owner.host))
				.andExpect(status().isGone());
		mockMvc.perform(get("/api/web/campaigns/{id}/links", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void blocksAddingMoreThanTenActiveFields() throws Exception {
		Owner owner = newOwner();
		Long templateId = createTemplate(owner, "field_01");
		for (int i = 2; i <= 10; i++) {
			addField(owner, templateId, "field_%02d".formatted(i)).andExpect(status().isCreated());
		}
		addField(owner, templateId, "field_11").andExpect(status().isBadRequest());
	}

	@Test
	void apiKeySingleLinkIdempotencyReplaysAndConflicts() throws Exception {
		Owner owner = newOwner();
		Long campaignId = createCampaign(owner, "API 캠페인", null);
		ApiKeyService.CreatedKey key = apiKeyService.create(owner.userId, owner.projectId, "automation",
				java.util.Set.of(ApiKeyScope.LINKS_WRITE, ApiKeyScope.CAMPAIGNS_WRITE), null);
		String auth = "Bearer " + key.rawKey();

		MvcResult first = mockMvc.perform(post("/api/v1/campaigns/{id}/links", campaignId)
					.header("Authorization", auth).header("Idempotency-Key", "req-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/one\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		String firstCode = readJson(first, "code");

		MvcResult retry = mockMvc.perform(post("/api/v1/campaigns/{id}/links", campaignId)
					.header("Authorization", auth).header("Idempotency-Key", "req-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/one\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		assertThat(readJson(retry, "code")).isEqualTo(firstCode);

		mockMvc.perform(post("/api/v1/campaigns/{id}/links", campaignId)
					.header("Authorization", auth).header("Idempotency-Key", "req-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/DIFFERENT\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
	}

	@Test
	void jsonBatchCreatesAllLinksAtomicallyAndReplaysIdempotently() throws Exception {
		Owner owner = newOwner();
		Long campaignId = createCampaign(owner, "배치 캠페인", null);
		ApiKeyService.CreatedKey key = apiKeyService.create(owner.userId, owner.projectId, "batch",
				java.util.Set.of(ApiKeyScope.LINKS_WRITE), null);
		String auth = "Bearer " + key.rawKey();
		String batchBody = "[{\"originalUrl\":\"https://example.com/b1\",\"externalId\":\"b-1\"},"
				+ "{\"originalUrl\":\"https://example.com/b2\",\"externalId\":\"b-2\"}]";

		MvcResult first = mockMvc.perform(post("/api/v1/campaigns/{id}/links/batch", campaignId)
					.header("Authorization", auth).header("Idempotency-Key", "batch-1")
					.contentType(MediaType.APPLICATION_JSON).content(batchBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.length()").value(2))
				.andReturn();

		mockMvc.perform(post("/api/v1/campaigns/{id}/links/batch", campaignId)
					.header("Authorization", auth).header("Idempotency-Key", "batch-1")
					.contentType(MediaType.APPLICATION_JSON).content(batchBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(post("/api/v1/campaigns/{id}/links/batch", campaignId)
					.header("Authorization", auth)
					.contentType(MediaType.APPLICATION_JSON).content(batchBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	void csvUploadProcessesValidRowsAndRecordsRowLevelErrors() throws Exception {
		Owner owner = newOwner();
		Long templateId = createTemplate(owner, "utm_source");
		Long campaignId = createCampaign(owner, "CSV 캠페인", templateId);
		String csv = "original_url,external_id,utm_source\n"
				+ "https://example.com/ok,csv-1,newsletter\n"
				+ "not-a-valid-url,csv-2,newsletter\n";
		MockMultipartFile file = new MockMultipartFile("file", "links.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

		MvcResult uploaded = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.multipart("/api/web/campaigns/{id}/imports/csv", campaignId)
					.file(file).with(csrf()).cookie(owner.cookie).header("Idempotency-Key", "csv-1"))
				.andExpect(status().isAccepted())
				.andReturn();
		Long importId = Long.valueOf(readJson(uploaded, "id"));

		for (int i = 0; i < 10 && !isImportTerminal(campaignId, importId, owner); i++) {
			importWorker.tick();
		}

		mockMvc.perform(get("/api/web/campaigns/{id}/imports/{importId}", campaignId, importId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.succeededRows").value(1))
				.andExpect(jsonPath("$.failedRows").value(1));
	}

	@Test
	void exportsLinksCsvWithAndWithoutExternalIdFilter() throws Exception {
		Owner owner = newOwner();
		Long templateId = createTemplate(owner, "utm_source");
		Long campaignId = createCampaign(owner, "내보내기 캠페인", templateId);
		mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/a\",\"externalId\":\"match-1\",\"utmValues\":{\"utm_source\":\"newsletter\"}}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/web/campaigns/{id}/links", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/b\",\"externalId\":\"other-2\",\"utmValues\":{\"utm_source\":\"newsletter\"}}"))
				.andExpect(status().isCreated());

		MvcResult noFilter = mockMvc.perform(get("/api/web/campaigns/{id}/links.csv", campaignId).cookie(owner.cookie))
				.andExpect(status().isOk())
				.andReturn();
		String noFilterBody = noFilter.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(noFilterBody).contains("match-1").contains("other-2");

		MvcResult filtered = mockMvc.perform(get("/api/web/campaigns/{id}/links.csv", campaignId)
					.param("externalId", "match").cookie(owner.cookie))
				.andExpect(status().isOk())
				.andReturn();
		String filteredBody = filtered.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(filteredBody).contains("match-1").doesNotContain("other-2");
	}

	private boolean isImportTerminal(Long campaignId, Long importId, Owner owner) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/web/campaigns/{id}/imports/{importId}", campaignId, importId).cookie(owner.cookie)).andReturn();
		return !readJson(result, "status").equals("PENDING") && !readJson(result, "status").equals("PROCESSING");
	}

	private org.springframework.test.web.servlet.ResultActions addField(Owner owner, Long templateId, String name) throws Exception {
		return mockMvc.perform(post("/api/web/projects/{projectId}/utm-templates/{templateId}/fields", owner.projectId, templateId)
				.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"));
	}

	private Long createTemplate(Owner owner, String firstFieldName) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/web/projects/{projectId}/utm-templates", owner.projectId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"template-" + (++counter) + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		Long templateId = Long.valueOf(readJson(created, "id"));
		addField(owner, templateId, firstFieldName).andExpect(status().isCreated());
		return templateId;
	}

	private Long createCampaign(Owner owner, String name, Long templateId) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/web/projects/{projectId}/campaigns", owner.projectId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		Long campaignId = Long.valueOf(readJson(created, "id"));
		if (templateId != null) {
			mockMvc.perform(patch("/api/web/campaigns/{id}/utm-template", campaignId)
						.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
						.content("{\"utmTemplateId\":" + templateId + "}"))
					.andExpect(status().isOk());
		}
		return campaignId;
	}

	private void updateUtmDefault(Owner owner, Long campaignId, String value) throws Exception {
		mockMvc.perform(patch("/api/web/campaigns/{id}/utm-defaults", campaignId)
					.with(csrf()).cookie(owner.cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"defaults\":{\"utm_source\":\"" + value + "\"}}"))
				.andExpect(status().isOk());
	}

	private Owner newOwner() {
		LoginResolution login = identityService.resolve(identity("owner-" + (++counter), "owner" + counter + "@example.com"));
		ProjectMember membership = projectMemberRepository.findByIdUserId(login.user().getId()).getFirst();
		Long projectId = membership.getProject().getId();
		String host = "srrrg.link";
		Cookie cookie = new Cookie("srrrg_access", sessionService.issue(login.user()).accessToken());
		return new Owner(login.user().getId(), projectId, host, cookie);
	}

	private OAuthIdentity identity(String subject, String email) {
		return new OAuthIdentity(OAuthProvider.GOOGLE, subject, email, true, "테스트 사용자");
	}

	private String readJson(MvcResult result, String field) throws Exception {
		com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(result.getResponse().getContentAsString());
		return node.get(field).asText();
	}

	private record Owner(Long userId, Long projectId, String host, Cookie cookie) { }
}

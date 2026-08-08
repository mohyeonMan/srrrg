package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import link.srrrg.auth.WebSessionService.SessionTokens;
import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthIdentityService;
import link.srrrg.identity.OAuthIdentityService.LoginResolution;
import link.srrrg.identity.OAuthProvider;
import link.srrrg.identity.OAuthAccountRepository;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.ApiKeyService;
import link.srrrg.project.InvitationEmailSender;
import link.srrrg.project.ProjectInvitation;
import link.srrrg.project.ProjectInvitationRepository;
import link.srrrg.project.ProjectApiKeyRepository;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRole;
import link.srrrg.project.ProjectService;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.link.management.dto.CreateLinkRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WebAuthPostgreSqlIntegrationTest {

	private static final String JWT_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

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
	}

	@Autowired
	OAuthIdentityService identityService;

	@Autowired
	OAuthAccountLinkService accountLinkService;

	@Autowired
	WebSessionService sessionService;

	@Autowired
	RefreshTokenService refreshTokenService;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	OAuthAuthorizationRequestRepository authorizationRequestRepository;

	@Autowired
	OAuthAccountRepository accountRepository;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApiKeyService apiKeyService;

	@Autowired
	ProjectMemberRepository projectMemberRepository;

	@Autowired
	ProjectInvitationRepository projectInvitationRepository;

	@Autowired
	ProjectService projectService;

	@Autowired
	ProjectApiKeyRepository projectApiKeyRepository;

	@Autowired
	LinkManagementService linkManagementService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	InvitationEmailSender invitationEmailSender;

	@Test
	void linksProviderOnlyAfterExistingAccountLogin() {
		LoginResolution google = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "google-1", "same@example.com"));
		LoginResolution kakao = identityService.resolve(identity(
				OAuthProvider.KAKAO, "kakao-1", "same@example.com"));

		assertThat(kakao.requiresLink()).isTrue();
		OAuthAccountLinkService.PendingLink pending = accountLinkService.create(
				kakao.user(), kakao.pendingIdentity(), "/manage");
		assertThat(accountLinkService.complete(pending.rawToken(), google.user())).isEqualTo("/manage");

		LoginResolution linked = identityService.resolve(identity(
				OAuthProvider.KAKAO, "kakao-1", "same@example.com"));
		assertThat(linked.requiresLink()).isFalse();
		assertThat(linked.user().getId()).isEqualTo(google.user().getId());

		LoginResolution github = identityService.resolve(identity(
				OAuthProvider.GITHUB, "github-1", "same@example.com"));
		OAuthAccountLinkService.PendingLink githubPending = accountLinkService.create(
				github.user(), github.pendingIdentity(), "/");
		accountLinkService.complete(githubPending.rawToken(), google.user());
		assertThat(identityService.resolve(identity(
				OAuthProvider.GITHUB, "github-1", "same@example.com")).user().getId())
				.isEqualTo(google.user().getId());
	}

	@Test
	void allowsLoginWithoutEmailAndNeverMergesUnverifiedEmail() {
		LoginResolution verified = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "verified-owner", "same-unverified@example.com"));
		LoginResolution missing = identityService.resolve(new OAuthIdentity(
				OAuthProvider.KAKAO, "kakao-no-email", null, false, null));
		LoginResolution repeated = identityService.resolve(new OAuthIdentity(
				OAuthProvider.KAKAO, "kakao-no-email", null, false, null));
		LoginResolution unverified = identityService.resolve(new OAuthIdentity(
				OAuthProvider.GITHUB, "github-unverified", "same-unverified@example.com", false, "GitHub User"));

		assertThat(missing.requiresLink()).isFalse();
		assertThat(missing.user().getEmail()).isNull();
		assertThat(missing.user().getEmailVerifiedAt()).isNull();
		assertThat(missing.user().getDisplayName()).isEqualTo("srrrg 사용자");
		assertThat(repeated.user().getId()).isEqualTo(missing.user().getId());
		assertThat(unverified.requiresLink()).isFalse();
		assertThat(unverified.user().getId()).isNotEqualTo(verified.user().getId());
		assertThat(unverified.user().getEmail()).isNull();
		assertThat(accountRepository.findByProviderAndProviderUserId(
				OAuthProvider.GITHUB, "github-unverified").orElseThrow().isProviderEmailVerified()).isFalse();
	}

	@Test
	void rotatesRefreshTokenAndRevokesFamilyOnReuse() {
		LoginResolution login = identityService.resolve(identity(
				OAuthProvider.GITHUB, "github-refresh", "refresh@example.com"));
		SessionTokens initial = sessionService.issue(login.user());
		SessionTokens rotated = sessionService.refresh(initial.refreshToken());

		assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());
		assertThatThrownBy(() -> sessionService.refresh(initial.refreshToken()))
				.isInstanceOf(RefreshTokenReuseException.class);
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getUser().getId().equals(login.user().getId()))
				.allMatch(token -> token.getRevokedAt() != null);
	}

	@Test
	void supportsCurrentDeviceAndAllDeviceLogout() {
		LoginResolution login = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "google-logout", "logout@example.com"));
		SessionTokens firstDevice = sessionService.issue(login.user());
		SessionTokens secondDevice = sessionService.issue(login.user());

		refreshTokenService.logoutCurrent(firstDevice.refreshToken());
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getTokenHash().equals(TokenHash.sha256(firstDevice.refreshToken())))
				.allMatch(token -> token.getRevokedAt() != null);
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getTokenHash().equals(TokenHash.sha256(secondDevice.refreshToken())))
				.allMatch(token -> token.getRevokedAt() == null);

		refreshTokenService.logoutAll(secondDevice.refreshToken());
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getUser().getId().equals(login.user().getId()))
				.allMatch(token -> token.getRevokedAt() != null);
	}

	@Test
	void preservesAnonymousRoutesAndProtectsCookiePostWithCsrf() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Google로 계속")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Kakao로 계속")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("GitHub로 계속")));

		mockMvc.perform(post("/api/links")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/web/auth/logout"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@Test
	void startsAllProviderLoginsWithDatabaseStateAndPkce() throws Exception {
		assertAuthorizationRedirect("google", "https://accounts.google.com/o/oauth2/v2/auth");
		assertAuthorizationRedirect("kakao", "https://kauth.kakao.com/oauth/authorize");
		assertAuthorizationRedirect("github", "https://github.com/login/oauth/authorize");
		assertThat(authorizationRequestRepository.count()).isGreaterThanOrEqualTo(3);
	}

	@Test
	void allowsOnlyMatchingProjectAndActiveApiKeyScope() throws Exception {
		LoginResolution login = identityService.resolve(identity(OAuthProvider.GOOGLE, "api-key-user", "api-key@example.com"));
		Long projectId = projectMemberRepository.findByIdUserId(login.user().getId()).getFirst().getProject().getId();
		ApiKeyService.CreatedKey created = apiKeyService.create(login.user().getId(), projectId, "automation", java.util.Set.of(ApiKeyScope.LINKS_READ), null);
		assertThat(created.rawKey()).startsWith("srrrg_pk_" + created.key().getKeyPrefix() + "_");
		assertThat(created.key().getKeyHash()).hasSize(64).isNotEqualTo(created.rawKey());
		assertThat(created.key().getExpiresAt()).isNull();

		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + created.rawKey()))
				.andExpect(status().isOk())
				.andExpect(header().exists("X-Request-Id"));
		assertThat(projectApiKeyRepository.findById(created.key().getId()).orElseThrow().getLastUsedAt()).isNotNull();
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId + 1)
					.header("Authorization", "Bearer " + created.rawKey()))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"));

		ApiKeyService.CreatedKey missingScope = apiKeyService.create(login.user().getId(), projectId, "campaigns", java.util.Set.of(ApiKeyScope.CAMPAIGNS_READ), null);
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + missingScope.rawKey()))
				.andExpect(status().isForbidden());

		jakarta.servlet.http.Cookie jwtCookie = new jakarta.servlet.http.Cookie(
				"srrrg_access", sessionService.issue(login.user()).accessToken());
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId).cookie(jwtCookie))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/web/projects").header("Authorization", "Bearer " + created.rawKey()))
				.andExpect(status().isUnauthorized());

		ApiKeyService.CreatedKey expired = apiKeyService.create(login.user().getId(), projectId, "expired", java.util.Set.of(ApiKeyScope.LINKS_READ), null);
		jdbcTemplate.update("UPDATE project_api_keys SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?", expired.key().getId());
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + expired.rawKey()))
				.andExpect(status().isUnauthorized());

		apiKeyService.revoke(login.user().getId(), projectId, created.key().getId());
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + created.rawKey()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsRawApiKeyOnlyOnCreation() throws Exception {
		LoginResolution owner = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "api-key-web-owner", "api-key-web@example.com"));
		Long projectId = projectMemberRepository.findByIdUserId(owner.user().getId()).getFirst().getProject().getId();
		jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(
				"srrrg_access", sessionService.issue(owner.user()).accessToken());

		mockMvc.perform(post("/api/web/projects/{projectId}/api-keys", projectId)
					.with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"automation\",\"scopes\":[\"links:read\"]}"))
				.andExpect(status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.key", org.hamcrest.Matchers.startsWith("srrrg_pk_")));
		mockMvc.perform(get("/api/web/projects/{projectId}/api-keys", projectId).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].key").doesNotExist())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].keyHash").doesNotExist());
	}

	@Test
	void exposesOnlyAnonymousAndApiKeyEndpointsInPublicOpenApi() throws Exception {
		mockMvc.perform(get("/srrrg-dev/docs/api")
					.contextPath("/srrrg-dev")
					.servletPath("/docs/api"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("content=\"/srrrg-dev/\"")));
		mockMvc.perform(get("/openapi.json"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/v3/api-docs/public"));
		mockMvc.perform(get("/v3/api-docs/public"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paths['/api/links']").exists())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paths['/api/v1/projects/{projectId}/links']").exists())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paths['/api/web/projects']").doesNotExist())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.components.securitySchemes.projectApiKey").exists());
	}

	@Test
	void completesInvitationFlowAndBlocksCrossProjectAccess() throws Exception {
		LoginResolution owner = identityService.resolve(identity(OAuthProvider.GOOGLE, "project-owner", "owner@example.com"));
		LoginResolution invitee = identityService.resolve(new OAuthIdentity(
				OAuthProvider.KAKAO, "project-invitee", null, false, "Invitee"));
		Long projectId = projectMemberRepository.findByIdUserId(owner.user().getId()).getFirst().getProject().getId();
		jakarta.servlet.http.Cookie ownerCookie = new jakarta.servlet.http.Cookie(
				"srrrg_access", sessionService.issue(owner.user()).accessToken());
		jakarta.servlet.http.Cookie inviteeCookie = new jakarta.servlet.http.Cookie(
				"srrrg_access", sessionService.issue(invitee.user()).accessToken());

		mockMvc.perform(post("/api/web/projects/{projectId}/invitations", projectId)
					.with(csrf()).cookie(ownerCookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"contact@example.com\",\"role\":\"EDITOR\"}"))
				.andExpect(status().isCreated());
		ProjectInvitation invitation = projectInvitationRepository.findByProjectId(projectId).getFirst();
		org.mockito.ArgumentCaptor<String> url = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(invitationEmailSender).send(eq("contact@example.com"), anyString(), url.capture());
		String rawToken = url.getValue().substring(url.getValue().lastIndexOf('/') + 1);
		assertThat(invitation.getTokenHash()).hasSize(64).isNotEqualTo(rawToken);

		mockMvc.perform(post("/api/web/invitations/{token}/accept", rawToken)
					.with(csrf()).cookie(inviteeCookie))
				.andExpect(status().isOk());
		assertThat(projectMemberRepository.findByIdProjectIdAndIdUserId(projectId, invitee.user().getId())
				.orElseThrow().getRole()).isEqualTo(ProjectRole.EDITOR);

		jakarta.servlet.http.Cookie outsiderCookie = new jakarta.servlet.http.Cookie(
				"srrrg_access", sessionService.issue(identityService.resolve(identity(
						OAuthProvider.GITHUB, "project-outsider", "outsider@example.com")).user()).accessToken());
		mockMvc.perform(get("/api/web/projects/{projectId}/members", projectId).cookie(ownerCookie))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/web/projects/{projectId}/members", projectId).cookie(outsiderCookie))
				.andExpect(status().isForbidden());

		ProjectInvitation stored = projectInvitationRepository.findById(invitation.getId()).orElseThrow();
		assertThat(stored.getAcceptedAt()).isNotNull();
	}

	@Test
	void preventsDuplicateInvitationAndInvalidatesCancelledOrResentTokens() {
		LoginResolution owner = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "invitation-owner", "invitation-owner@example.com"));
		LoginResolution invitee = identityService.resolve(identity(
				OAuthProvider.GITHUB, "invitation-target", "invitation-target@example.com"));
		Long projectId = projectMemberRepository.findByIdUserId(owner.user().getId()).getFirst().getProject().getId();

		ProjectInvitation first = projectService.invite(
				owner.user().getId(), projectId, "resend@example.com", ProjectRole.VIEWER);
		assertThatThrownBy(() -> projectService.invite(
				owner.user().getId(), projectId, "RESEND@example.com", ProjectRole.EDITOR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미 활성 상태인 초대가 있습니다.");
		ProjectInvitation resent = projectService.resend(owner.user().getId(), first.getId());
		ProjectInvitation cancelled = projectService.invite(
				owner.user().getId(), projectId, "cancel@example.com", ProjectRole.VIEWER);
		projectService.cancel(owner.user().getId(), cancelled.getId());

		org.mockito.ArgumentCaptor<String> urls = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(invitationEmailSender, times(3)).send(anyString(), anyString(), urls.capture());
		String firstToken = tokenFrom(urls.getAllValues().get(0));
		String resentToken = tokenFrom(urls.getAllValues().get(1));
		String cancelledToken = tokenFrom(urls.getAllValues().get(2));
		assertThat(firstToken).isNotEqualTo(resentToken);
		assertThatThrownBy(() -> projectService.accept(invitee.user().getId(), firstToken))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> projectService.accept(invitee.user().getId(), cancelledToken))
				.isInstanceOf(IllegalStateException.class);
		assertThat(projectService.accept(invitee.user().getId(), resentToken).projectId()).isEqualTo(projectId);
		assertThat(projectInvitationRepository.findById(resent.getId()).orElseThrow().getAcceptedAt()).isNotNull();
	}

	@Test
	void managesProjectMetadataClaimsLinkAndSoftDeletesProject() throws Exception {
		LoginResolution owner = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "project-metadata-owner", "metadata-owner@example.com"));
		var membership = projectMemberRepository.findByIdUserId(owner.user().getId()).getFirst();
		Long projectId = membership.getProject().getId();
		String slug = membership.getProject().getSlug();
		String projectHost = slug + ".srrrg.link";
		jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(
				"srrrg_access", sessionService.issue(owner.user()).accessToken());

		assertThat(slug).matches("p-[a-z0-9]{8}");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT created_by_user_id FROM projects WHERE id = ?", Long.class, projectId))
				.isEqualTo(owner.user().getId());
		Long domainId = jdbcTemplate.queryForObject(
				"SELECT id FROM project_domains WHERE project_id = ?", Long.class, projectId);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT hostname FROM project_domains WHERE id = ?", String.class, domainId))
				.isEqualTo(projectHost);
		mockMvc.perform(get("/api/web/projects/{projectId}", projectId).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.slug").value(slug));
		mockMvc.perform(get("/api/web/projects/{projectId}/domains", projectId).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].hostname").value(projectHost));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
					"/api/web/projects/{projectId}", projectId)
					.with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"새 프로젝트 이름\"}"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name").value("새 프로젝트 이름"));
		String webCode = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/web/projects/{projectId}/links", projectId)
					.with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/web-project\"}"))
				.andExpect(status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.secretKey").doesNotExist())
				.andReturn().getResponse().getContentAsString(), "$.code");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT created_by_user_id FROM links WHERE code = ?", Long.class, webCode))
				.isEqualTo(owner.user().getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT secret_key_hash IS NULL FROM links WHERE code = ?", Boolean.class, webCode)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT domain_id FROM links WHERE code = ?", Long.class, webCode)).isEqualTo(domainId);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT hostname FROM links WHERE code = ?", String.class, webCode)).isEqualTo(projectHost);
		String previousProjectHost = projectHost;
		projectHost = "renamed-project.srrrg.link";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
					"/api/web/projects/{projectId}/domains/{domainId}", projectId, domainId)
					.with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"slug\":\"renamed-project\"}"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.hostname").value(projectHost));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT slug FROM projects WHERE id = ?", String.class, projectId)).isEqualTo("renamed-project");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT domain_id FROM links WHERE code = ?", Long.class, webCode)).isEqualTo(domainId);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT hostname FROM links WHERE code = ?", String.class, webCode)).isEqualTo(previousProjectHost);
		mockMvc.perform(get("/{code}", webCode).header("Host", previousProjectHost))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://example.com/web-project"));
		mockMvc.perform(get("/{code}", webCode)
					.header("Host", projectHost)
					.header("X-Forwarded-Host", "attacker.example"))
				.andExpect(status().isNotFound());
		String newCode = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/web/projects/{projectId}/links", projectId)
					.with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/new-domain\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.code");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT hostname FROM links WHERE code = ?", String.class, newCode)).isEqualTo(projectHost);
		mockMvc.perform(get("/{code}", newCode).header("Host", projectHost))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://example.com/new-domain"));
		mockMvc.perform(get("/{code}", webCode).header("Host", "srrrg.link"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/{code}", webCode).header("Host", "unregistered.srrrg.link"))
				.andExpect(status().isNotFound());
		LoginResolution otherOwner = identityService.resolve(identity(
				OAuthProvider.GITHUB, "project-domain-other", "domain-other@example.com"));
		Long otherProjectId = projectMemberRepository.findByIdUserId(otherOwner.user().getId()).getFirst().getProject().getId();
		Long otherDomainId = jdbcTemplate.queryForObject(
				"SELECT id FROM project_domains WHERE project_id = ?", Long.class, otherProjectId);
		projectService.changeDomain(otherOwner.user().getId(), otherProjectId, otherDomainId, slug);
		jdbcTemplate.update("""
				INSERT INTO links (code, original_url, project_id, domain_id, hostname)
				VALUES ('Other1', 'https://example.com/other-project', ?, ?, ?)
				""", otherProjectId, otherDomainId, previousProjectHost);
		mockMvc.perform(get("/Other1").header("Host", previousProjectHost))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://example.com/other-project"));
		mockMvc.perform(get("/{code}", webCode).header("Host", previousProjectHost))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://example.com/web-project"));
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO links (code, original_url, project_id, domain_id, hostname)
				VALUES (?, 'https://example.com/conflict', ?, ?, ?)
				""", webCode, otherProjectId, otherDomainId, previousProjectHost))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO links (code, original_url, project_id, domain_id, hostname)
				VALUES ('BadMap', 'https://example.com', ?, ?, ?)
				""", projectId, otherDomainId, "renamed-project.srrrg.link"))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

		var anonymous = linkManagementService.create(new CreateLinkRequest("https://example.com/project", null));
		mockMvc.perform(post("/api/web/projects/{projectId}/links/{code}/claim", projectId, anonymous.code())
					.with(csrf()).cookie(cookie).header("X-Srrrg-Secret-Key", anonymous.secretKey()))
				.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT created_by_user_id FROM links WHERE code = ?", Long.class, anonymous.code()))
				.isEqualTo(owner.user().getId());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT secret_key_hash IS NULL FROM links WHERE code = ?", Boolean.class, anonymous.code())).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT domain_id FROM links WHERE code = ?", Long.class, anonymous.code())).isEqualTo(domainId);
		var conflictingAnonymous = linkManagementService.create(
				new CreateLinkRequest("https://example.com/conflicting-claim", null));
		jdbcTemplate.update("UPDATE links SET code = ? WHERE code = ? AND project_id IS NULL", webCode, conflictingAnonymous.code());
		mockMvc.perform(post("/api/web/projects/{projectId}/links/{code}/claim", projectId, webCode)
					.with(csrf()).cookie(cookie).header("X-Srrrg-Secret-Key", conflictingAnonymous.secretKey()))
				.andExpect(status().isConflict())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("LINK_CODE_CONFLICT"));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT project_id IS NULL FROM links WHERE code = ? AND project_id IS NULL", Boolean.class, webCode)).isTrue();
		mockMvc.perform(get("/api/web/projects/{projectId}/overview", projectId).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.standaloneLinks[0].code").value(anonymous.code()))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.campaigns").isEmpty());
		ApiKeyService.CreatedKey apiKey = apiKeyService.create(owner.user().getId(), projectId, "before archive",
				java.util.Set.of(ApiKeyScope.LINKS_READ, ApiKeyScope.LINKS_WRITE), null);
		String apiCode = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + apiKey.rawKey())
					.header("Idempotency-Key", "project-create-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/api-project\"}"))
				.andExpect(status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.secretKey").doesNotExist())
				.andReturn().getResponse().getContentAsString(), "$.code");
		mockMvc.perform(post("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + apiKey.rawKey())
					.header("Idempotency-Key", "project-create-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/api-project\"}"))
				.andExpect(status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(apiCode));
		mockMvc.perform(post("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + apiKey.rawKey())
					.header("Idempotency-Key", "project-create-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com/different\"}"))
				.andExpect(status().isConflict())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT created_by_user_id IS NULL FROM links WHERE code = ?", Boolean.class, apiCode)).isTrue();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
					"/api/web/projects/{projectId}", projectId).with(csrf()).cookie(cookie))
				.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT archived_at IS NOT NULL FROM projects WHERE id = ?", Boolean.class, projectId)).isTrue();
		mockMvc.perform(get("/api/web/projects").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + apiKey.rawKey()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/{code}", anonymous.code()).header("Host", projectHost)).andExpect(status().isGone());
	}

	@Test
	void allowsOnlyOneConcurrentAnonymousLinkClaim() throws Exception {
		LoginResolution first = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "claim-first", "claim-first@example.com"));
		LoginResolution second = identityService.resolve(identity(
				OAuthProvider.GITHUB, "claim-second", "claim-second@example.com"));
		Long firstProject = projectMemberRepository.findByIdUserId(first.user().getId()).getFirst().getProject().getId();
		Long secondProject = projectMemberRepository.findByIdUserId(second.user().getId()).getFirst().getProject().getId();
		var anonymous = linkManagementService.create(new CreateLinkRequest("https://example.com/concurrent-claim", null));
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		try {
			java.util.concurrent.Callable<Boolean> firstClaim = () -> claimAfterStart(
					ready, start, first.user().getId(), firstProject, anonymous.code(), anonymous.secretKey());
			java.util.concurrent.Callable<Boolean> secondClaim = () -> claimAfterStart(
					ready, start, second.user().getId(), secondProject, anonymous.code(), anonymous.secretKey());
			var firstResult = executor.submit(firstClaim);
			var secondResult = executor.submit(secondClaim);
			assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(java.util.List.of(firstResult.get(), secondResult.get()).stream().filter(Boolean::booleanValue).count())
					.isEqualTo(1);
			assertThat(jdbcTemplate.queryForObject("SELECT project_id FROM links WHERE code = ?", Long.class, anonymous.code()))
					.isIn(firstProject, secondProject);
		} finally {
			executor.shutdownNow();
		}
	}

	private boolean claimAfterStart(java.util.concurrent.CountDownLatch ready, java.util.concurrent.CountDownLatch start,
			Long userId, Long projectId, String code, String secret) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			projectService.importAnonymousLink(userId, projectId, code, secret);
			return true;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private void assertAuthorizationRedirect(String provider, String authorizationUri) throws Exception {
		mockMvc.perform(get("/oauth2/authorization/{provider}", provider))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(authorizationUri)))
				.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge=")))
				.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge_method=S256")))
				.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
						org.hamcrest.Matchers.containsString("srrrg_oauth_request_"))));
	}

	private OAuthIdentity identity(OAuthProvider provider, String providerUserId, String email) {
		return new OAuthIdentity(provider, providerUserId, email, true, "Test User");
	}

	private String tokenFrom(String invitationUrl) {
		return invitationUrl.substring(invitationUrl.lastIndexOf('/') + 1);
	}
}

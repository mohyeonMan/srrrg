package link.srrrg.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import link.srrrg.HomeController;
import link.srrrg.campaign.CampaignService;
import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;
import link.srrrg.link.management.LinkController;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.link.management.dto.CreateLinkResponse;
import link.srrrg.project.ApiKeyService;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.PublicProjectLinkController;
import link.srrrg.project.Project;
import link.srrrg.project.InvitationPageController;
import link.srrrg.project.ProjectController;
import link.srrrg.project.ProjectRole;
import link.srrrg.project.ProjectService;
import link.srrrg.link.LinkRepository;
import link.srrrg.identity.UserRepository;

@WebMvcTest(controllers = {HomeController.class, LoginController.class, LinkController.class, AuthController.class, PublicProjectLinkController.class, InvitationPageController.class, ProjectController.class})
@Import({SecurityConfiguration.class, CsrfCookieFilter.class})
class SecurityWebTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ClientRegistrationRepository registrations;

	@MockitoBean
	DatabaseAuthorizationRequestRepository authorizationRequests;

	@MockitoBean
	ProviderOAuth2UserService oauth2UserService;

	@MockitoBean
	ProviderOidcUserService oidcUserService;

	@MockitoBean
	OAuthLoginSuccessHandler successHandler;

	@MockitoBean
	OAuthLoginFailureHandler failureHandler;

	@MockitoBean
	JwtService jwtService;

	@MockitoBean
	WebSessionService sessionService;

	@MockitoBean
	RefreshTokenService refreshTokenService;

	@MockitoBean
	WebTokenCookies tokenCookies;

	@MockitoBean
	LinkManagementService linkManagementService;

	@MockitoBean
	ApiKeyService apiKeyService;

	@MockitoBean
	ProjectService projectService;

	@MockitoBean
	LinkRepository linkRepository;

	@MockitoBean
	RateLimitService rateLimitService;

	@MockitoBean
	ClientRequestInfoResolver requestInfoResolver;

	@MockitoBean
	CampaignService campaignService;

	@MockitoBean
	UserRepository userRepository;

	@Test
	void rendersAccessibleLoginOptions() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Google로 계속")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Kakao로 계속")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("GitHub로 계속")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"소셜 로그인\"")));
	}

	@Test
	void rendersAccountManagementPage() throws Exception {
		mockMvc.perform(get("/account"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("내 정보")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("모든 기기에서 로그아웃")));
	}

	@Test
	void rendersFirstLoginProfilePage() throws Exception {
		mockMvc.perform(get("/onboarding"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("기본 정보를 확인해 주세요")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("설정 완료")));
	}

	@Test
	void preservesAnonymousLinkPostWithoutCsrf() throws Exception {
		when(requestInfoResolver.resolve(any())).thenReturn(new ClientRequestInfo("127.0.0.1", null, null));
		when(linkManagementService.create(any())).thenReturn(new CreateLinkResponse(
				"aB3x9Q", "https://srrrg.link/aB3x9Q", "srrrg_sk_secret", null));

		mockMvc.perform(post("/api/links")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("aB3x9Q"));
	}

	@Test
	void rejectsCookieAuthPostWithoutCsrfAsJson() throws Exception {
		mockMvc.perform(post("/api/web/auth/logout"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void acceptsRawCookieCsrfTokenFromBrowserHeader() throws Exception {
		MvcResult page = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
		jakarta.servlet.http.Cookie csrf = page.getResponse().getCookie("XSRF-TOKEN");
		org.junit.jupiter.api.Assertions.assertNotNull(csrf);

		mockMvc.perform(post("/api/web/auth/logout")
					.cookie(csrf)
					.header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isNoContent());
	}

	@Test
	void doesNotRotateCsrfTokenForEveryJwtAuthenticatedRequest() throws Exception {
		when(jwtService.verify("access-token")).thenReturn(1L);
		when(projectService.myMemberships(1L)).thenReturn(java.util.List.of());

		MvcResult result = mockMvc.perform(get("/api/web/projects")
					.cookie(new jakarta.servlet.http.Cookie("srrrg_access", "access-token"))
					.cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-token")))
				.andReturn();
		org.mockito.Mockito.verify(jwtService).verify("access-token");
		org.junit.jupiter.api.Assertions.assertEquals(200, result.getResponse().getStatus());
		org.junit.jupiter.api.Assertions.assertTrue(result.getResponse().getHeaders("Set-Cookie").isEmpty());
	}

	@Test
	void rejectsPublicApiWithoutApiKeyAndDoesNotUseJwtCookie() throws Exception {
		mockMvc.perform(get("/api/v1/projects/7/links"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("API_KEY_INVALID"));
	}

	@Test
	void allowsProjectLinksOnlyForMatchingKeyProjectAndScope() throws Exception {
		when(apiKeyService.authenticate("srrrg_pk_prefix_secret"))
				.thenReturn(new ApiKeyService.ApiKeyPrincipal(1L, 7L, java.util.Set.of(ApiKeyScope.LINKS_READ)));
		when(linkRepository.findByProjectIdAndCampaignIsNullOrderByIdDesc(eq(7L), any())).thenReturn(java.util.List.of());

		mockMvc.perform(get("/api/v1/projects/7/links").header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
	}

	@Test
	void appliesApiKeyFilterBehindContextPath() throws Exception {
		when(apiKeyService.authenticate("srrrg_pk_prefix_secret"))
				.thenReturn(new ApiKeyService.ApiKeyPrincipal(1L, 7L, java.util.Set.of(ApiKeyScope.LINKS_READ)));
		when(linkRepository.findByProjectIdAndCampaignIsNullOrderByIdDesc(eq(7L), any())).thenReturn(java.util.List.of());

		mockMvc.perform(get("/srrrg-dev/api/v1/projects/7/links")
					.contextPath("/srrrg-dev")
					.servletPath("/api/v1/projects/7/links")
					.header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isOk());
	}

	@Test
	void rendersInvitationPageWithContextPath() throws Exception {
		when(projectService.invitationPreview("token")).thenReturn(new ProjectService.InvitationPreview(
				true, "초대 프로젝트", ProjectRole.EDITOR, java.time.Instant.parse("2026-08-08T00:00:00Z")));

		mockMvc.perform(get("/srrrg-dev/invitations/token")
					.contextPath("/srrrg-dev")
					.servletPath("/invitations/token"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("content=\"/srrrg-dev/\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("<span>초대 프로젝트</span>에 초대받았습니다")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Google로 계속")));
	}

	@Test
	void rejectsAnotherProjectAndMissingScope() throws Exception {
		when(apiKeyService.authenticate("srrrg_pk_prefix_secret"))
				.thenReturn(new ApiKeyService.ApiKeyPrincipal(1L, 7L, java.util.Set.of(ApiKeyScope.CAMPAIGNS_READ)));

		mockMvc.perform(get("/api/v1/projects/8/links").header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/projects/7/links").header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SCOPE_REQUIRED"));
	}

	@Test
	void returnsCursorWhenMoreProjectLinksExist() throws Exception {
		link.srrrg.link.Link first = org.mockito.Mockito.mock(link.srrrg.link.Link.class);
		link.srrrg.link.Link second = org.mockito.Mockito.mock(link.srrrg.link.Link.class);
		when(first.getId()).thenReturn(10L);
		when(second.getId()).thenReturn(9L);
		when(apiKeyService.authenticate("srrrg_pk_prefix_secret"))
				.thenReturn(new ApiKeyService.ApiKeyPrincipal(1L, 7L, java.util.Set.of(ApiKeyScope.LINKS_READ)));
		when(linkRepository.findByProjectIdAndCampaignIsNullOrderByIdDesc(eq(7L), any())).thenReturn(java.util.List.of(first, second));

		mockMvc.perform(get("/api/v1/projects/7/links").param("limit", "1").header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").value(10));
	}

	@Test
	void claimsAnonymousLinkWithSecretHeader() throws Exception {
		MvcResult page = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
		jakarta.servlet.http.Cookie csrf = page.getResponse().getCookie("XSRF-TOKEN");
		org.junit.jupiter.api.Assertions.assertNotNull(csrf);
		when(jwtService.verify("access-token")).thenReturn(1L);
		jakarta.servlet.http.Cookie jwt = new jakarta.servlet.http.Cookie("srrrg_access", "access-token");

		mockMvc.perform(post("/api/web/projects/7/links/aB3x9Q/claim")
					.cookie(jwt, csrf).header("X-XSRF-TOKEN", csrf.getValue())
					.header("X-Srrrg-Secret-Key", "srrrg_sk_secret"))
				.andExpect(status().isNoContent());
		verify(projectService).importAnonymousLink(1L, 7L, "aB3x9Q", "srrrg_sk_secret");
	}

	@Test
	void deletesProjectLinkWithJwtAndCsrf() throws Exception {
		MvcResult page = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
		jakarta.servlet.http.Cookie csrf = page.getResponse().getCookie("XSRF-TOKEN");
		org.junit.jupiter.api.Assertions.assertNotNull(csrf);
		when(jwtService.verify("access-token")).thenReturn(1L);
		jakarta.servlet.http.Cookie jwt = new jakarta.servlet.http.Cookie("srrrg_access", "access-token");

		mockMvc.perform(delete("/api/web/projects/7/links/aB3x9Q")
					.cookie(jwt, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isNoContent());
		verify(projectService).deleteProjectLink(1L, 7L, "aB3x9Q");
	}

	@Test
	void readsAndUpdatesProjectLinkWithJwt() throws Exception {
		when(jwtService.verify("access-token")).thenReturn(1L);
		jakarta.servlet.http.Cookie jwt = new jakarta.servlet.http.Cookie("srrrg_access", "access-token");

		mockMvc.perform(get("/api/web/projects/7/links/aB3x9Q").cookie(jwt))
				.andExpect(status().isOk());
		verify(projectService).projectLink(1L, 7L, "aB3x9Q");

		MvcResult page = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
		jakarta.servlet.http.Cookie csrf = page.getResponse().getCookie("XSRF-TOKEN");
		org.junit.jupiter.api.Assertions.assertNotNull(csrf);
		mockMvc.perform(patch("/api/web/projects/7/links/aB3x9Q")
					.cookie(jwt, csrf).header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON).content("{\"originalUrl\":\"https://new.example\"}"))
				.andExpect(status().isOk());
		verify(projectService).updateProjectLink(eq(1L), eq(7L), eq("aB3x9Q"), any());
	}

	@Test
	void returnsProjectPlatformDomainToJwtMember() throws Exception {
		when(jwtService.verify("access-token")).thenReturn(1L);
		Project project = org.mockito.Mockito.mock(Project.class);
		when(project.getSubdomain()).thenReturn("acme");
		when(project.isSubdomainEnabled()).thenReturn(true);
		when(projectService.projectDomain(1L, 7L)).thenReturn(project);

		mockMvc.perform(get("/api/web/projects/7/subdomain")
				.cookie(new jakarta.servlet.http.Cookie("srrrg_access", "access-token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subdomain").value("acme"))
				.andExpect(jsonPath("$.enabled").value(true));
	}

	@Test
	void changesProjectPlatformDomainWithJwtAndCsrf() throws Exception {
		when(jwtService.verify("access-token")).thenReturn(1L);
		Project project = org.mockito.Mockito.mock(Project.class);
		when(project.getSubdomain()).thenReturn("renamed");
		when(projectService.claimSubdomain(1L, 7L, "renamed")).thenReturn(project);
		jakarta.servlet.http.Cookie jwt = new jakarta.servlet.http.Cookie("srrrg_access", "access-token");
		MvcResult page = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
		jakarta.servlet.http.Cookie csrf = page.getResponse().getCookie("XSRF-TOKEN");

		mockMvc.perform(put("/api/web/projects/7/subdomain")
					.cookie(jwt, csrf).header("X-XSRF-TOKEN", csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON).content("{\"subdomain\":\"renamed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subdomain").value("renamed"));
	}

	@Test
	void createsProjectLinkWithApiKeyWithoutJwtFilter() throws Exception {
		when(apiKeyService.authenticate("srrrg_pk_prefix_secret"))
				.thenReturn(new ApiKeyService.ApiKeyPrincipal(1L, 7L, java.util.Set.of(ApiKeyScope.LINKS_WRITE)));
		link.srrrg.link.Link link = org.mockito.Mockito.mock(link.srrrg.link.Link.class);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn("https://example.com");
		when(projectService.createProjectLink(eq(1L), eq(7L), eq("retry-1"), any())).thenReturn(link);

		mockMvc.perform(post("/api/v1/projects/7/links")
					.header("Authorization", "Bearer srrrg_pk_prefix_secret")
					.header("Idempotency-Key", "retry-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("aB3x9Q"));
		verify(projectService).createProjectLink(eq(1L), eq(7L), eq("retry-1"), any());
	}

	@Test
	void requiresLinksWriteScopeForProjectLinkCreation() throws Exception {
		when(apiKeyService.authenticate("srrrg_pk_prefix_secret"))
				.thenReturn(new ApiKeyService.ApiKeyPrincipal(1L, 7L, java.util.Set.of(ApiKeyScope.LINKS_READ)));

		mockMvc.perform(post("/api/v1/projects/7/links")
					.header("Authorization", "Bearer srrrg_pk_prefix_secret")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SCOPE_REQUIRED"));
	}
}

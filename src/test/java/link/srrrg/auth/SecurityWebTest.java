package link.srrrg.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import link.srrrg.link.management.LinkController;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.link.management.dto.CreateLinkResponse;
import link.srrrg.project.ApiKeyService;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.PublicProjectLinkController;
import link.srrrg.project.InvitationPageController;
import link.srrrg.link.LinkRepository;

@WebMvcTest(controllers = {HomeController.class, LoginController.class, LinkController.class, AuthController.class, PublicProjectLinkController.class, InvitationPageController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, CsrfCookieFilter.class})
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
	LinkRepository linkRepository;

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
	void preservesAnonymousLinkPostWithoutCsrf() throws Exception {
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
		when(linkRepository.findByProjectIdAndDeletedFalseOrderByIdDesc(eq(7L), any())).thenReturn(java.util.List.of());

		mockMvc.perform(get("/api/v1/projects/7/links").header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
	}

	@Test
	void appliesApiKeyFilterBehindContextPath() throws Exception {
		when(apiKeyService.authenticate("srrrg_pk_prefix_secret"))
				.thenReturn(new ApiKeyService.ApiKeyPrincipal(1L, 7L, java.util.Set.of(ApiKeyScope.LINKS_READ)));
		when(linkRepository.findByProjectIdAndDeletedFalseOrderByIdDesc(eq(7L), any())).thenReturn(java.util.List.of());

		mockMvc.perform(get("/srrrg-dev/api/v1/projects/7/links")
					.contextPath("/srrrg-dev")
					.servletPath("/api/v1/projects/7/links")
					.header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isOk());
	}

	@Test
	void rendersInvitationPageWithContextPath() throws Exception {
		mockMvc.perform(get("/srrrg-dev/invitations/token")
					.contextPath("/srrrg-dev")
					.servletPath("/invitations/token"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("content=\"/srrrg-dev/\"")));
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
		when(linkRepository.findByProjectIdAndDeletedFalseOrderByIdDesc(eq(7L), any())).thenReturn(java.util.List.of(first, second));

		mockMvc.perform(get("/api/v1/projects/7/links").param("limit", "1").header("Authorization", "Bearer srrrg_pk_prefix_secret"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").value(10));
	}
}

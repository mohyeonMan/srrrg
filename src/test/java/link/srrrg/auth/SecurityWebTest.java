package link.srrrg.auth;

import static org.mockito.ArgumentMatchers.any;
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

import link.srrrg.HomeController;
import link.srrrg.link.management.LinkController;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.link.management.dto.CreateLinkResponse;

@WebMvcTest(controllers = {HomeController.class, LoginController.class, LinkController.class, AuthController.class})
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
}

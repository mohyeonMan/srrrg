package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebTokenCookiesTest {

	@Test
	void writesHostOnlySecureHttpOnlyCookiesWithContextAwareRefreshPath() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContextPath("/srrrg-dev");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new WebTokenCookies().write(request, response,
				new WebSessionService.SessionTokens("access", "refresh"));

		assertThat(response.getHeaders("Set-Cookie"))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_access=access", "Path=/", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_refresh=refresh", "Path=/srrrg-dev/api/web/auth", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="));
	}
}

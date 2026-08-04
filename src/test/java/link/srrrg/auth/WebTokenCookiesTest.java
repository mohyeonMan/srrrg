package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class WebTokenCookiesTest {

	@Test
	void writesHostOnlySecureHttpOnlyCookiesWithNarrowRefreshPath() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new WebTokenCookies().write(response,
				new WebSessionService.SessionTokens("access", "refresh"));

		assertThat(response.getHeaders("Set-Cookie"))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_access=access", "Path=/", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_refresh=refresh", "Path=/api/web/auth", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="));
	}
}

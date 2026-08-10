package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebTokenCookiesTest {

	@Test
	void writesHostOnlySecureHttpOnlyCookiesWithContextAwareRefreshPath() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContextPath("/srrrg-dev");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new WebTokenCookies("https://srrrg.link", Duration.ofMinutes(5)).write(request, response,
				new WebSessionService.SessionTokens("access", "refresh"));

		assertThat(response.getHeaders("Set-Cookie"))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_access=access", "Path=/", "Max-Age=300", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_refresh=refresh", "Path=/srrrg-dev/api/web/auth", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="));
	}

	@Test
	void allowsCookiesOnLocalHttp() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new WebTokenCookies("http://localhost:8080", Duration.ofMinutes(5)).write(new MockHttpServletRequest(), response,
				new WebSessionService.SessionTokens("access", "refresh"));

		assertThat(response.getHeaders("Set-Cookie"))
				.allSatisfy(cookie -> assertThat(cookie).doesNotContain("Secure"));
	}
}

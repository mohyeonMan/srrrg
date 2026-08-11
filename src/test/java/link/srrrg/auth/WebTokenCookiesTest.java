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

		new WebTokenCookies("https://srrrg.link").write(request, response,
				new WebSessionService.SessionTokens("access", "refresh"));

		// access token 은 짧게 만료되지만 cookie 는 세션 기간(30일) 동안 유지한다.
		// 만료와 함께 cookie 를 지우면 브라우저가 아무것도 보내지 않아 헤더가 로그아웃으로 그려진다.
		assertThat(response.getHeaders("Set-Cookie"))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_access=access", "Path=/", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="))
				.anySatisfy(cookie -> assertThat(cookie)
						.contains("srrrg_refresh=refresh", "Path=/srrrg-dev/api/web/auth", "Secure", "HttpOnly", "SameSite=Lax")
						.doesNotContain("Domain="));
	}

	@Test
	void allowsCookiesOnLocalHttp() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new WebTokenCookies("http://localhost:8080").write(new MockHttpServletRequest(), response,
				new WebSessionService.SessionTokens("access", "refresh"));

		assertThat(response.getHeaders("Set-Cookie"))
				.allSatisfy(cookie -> assertThat(cookie).doesNotContain("Secure"));
	}
}

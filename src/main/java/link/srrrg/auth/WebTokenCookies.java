package link.srrrg.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.WebSessionService.SessionTokens;

@Component
public class WebTokenCookies {

	public static final String ACCESS_COOKIE = "srrrg_access";
	public static final String REFRESH_COOKIE = "srrrg_refresh";
	private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
	private final boolean secureCookies;

	WebTokenCookies(@Value("${srrrg.base-url}") String baseUrl) {
		secureCookies = baseUrl.startsWith("https://");
	}

	public void write(HttpServletRequest request, HttpServletResponse response, SessionTokens tokens) {
		// access token 자체는 짧게 만료되지만 cookie 는 세션 기간 동안 남긴다.
		// cookie 를 token 만료와 함께 지우면 브라우저가 아무것도 보내지 않아,
		// refresh token 으로 세션이 살아 있는데도 서버가 헤더를 로그아웃으로 그리게 된다.
		// 만료된 token 은 JwtAuthenticationFilter 가 그대로 거부하므로 권한에는 영향이 없다.
		add(response, ACCESS_COOKIE, tokens.accessToken(), "/", SESSION_LIFETIME);
		add(response, REFRESH_COOKIE, tokens.refreshToken(), authPath(request), SESSION_LIFETIME);
	}

	public void clear(HttpServletRequest request, HttpServletResponse response) {
		add(response, ACCESS_COOKIE, "", "/", Duration.ZERO);
		add(response, REFRESH_COOKIE, "", authPath(request), Duration.ZERO);
	}

	private String authPath(HttpServletRequest request) {
		return request.getContextPath() + "/api/web/auth";
	}

	private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(secureCookies)
				.sameSite("Lax")
				.path(path)
				.maxAge(maxAge)
				.build().toString());
	}
}

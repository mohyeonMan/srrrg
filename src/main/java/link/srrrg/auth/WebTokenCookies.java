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
	private final boolean secureCookies;
	private final Duration accessLifetime;

	WebTokenCookies(@Value("${srrrg.base-url}") String baseUrl,
			@Value("${srrrg.auth.jwt.access-lifetime:5m}") Duration accessLifetime) {
		secureCookies = baseUrl.startsWith("https://");
		this.accessLifetime = accessLifetime;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, SessionTokens tokens) {
		add(response, ACCESS_COOKIE, tokens.accessToken(), "/", accessLifetime);
		add(response, REFRESH_COOKIE, tokens.refreshToken(), authPath(request), Duration.ofDays(30));
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

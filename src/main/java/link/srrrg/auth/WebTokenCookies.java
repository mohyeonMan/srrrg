package link.srrrg.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.WebSessionService.SessionTokens;

@Component
public class WebTokenCookies {

	public static final String ACCESS_COOKIE = "srrrg_access";
	public static final String REFRESH_COOKIE = "srrrg_refresh";

	public void write(HttpServletResponse response, SessionTokens tokens) {
		add(response, ACCESS_COOKIE, tokens.accessToken(), "/", Duration.ofMinutes(15));
		add(response, REFRESH_COOKIE, tokens.refreshToken(), "/api/web/auth", Duration.ofDays(30));
	}

	public void clear(HttpServletResponse response) {
		add(response, ACCESS_COOKIE, "", "/", Duration.ZERO);
		add(response, REFRESH_COOKIE, "", "/api/web/auth", Duration.ZERO);
	}

	private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(path)
				.maxAge(maxAge)
				.build().toString());
	}
}

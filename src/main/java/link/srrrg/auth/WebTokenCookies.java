package link.srrrg.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.WebSessionService.SessionTokens;

/**
 * 세션 토큰을 브라우저 쿠키로 내보내고 지운다. 두 토큰의 쿠키 속성이 다른 것이 이 클래스의 핵심이다.
 *
 * <p>refresh token 쿠키는 경로를 {@code /api/web/auth}로 좁힌다. 갱신과 로그아웃 요청에만 전송되므로
 * 나머지 요청에서는 브라우저가 아예 보내지 않고, 그만큼 노출 지점이 줄어든다.
 * access token 쿠키는 모든 API 요청에 필요하므로 경로가 전체다.</p>
 *
 * <p>둘 다 HttpOnly라 스크립트가 읽을 수 없고, SameSite=Lax라 외부 사이트에서 시작된 교차 사이트
 * POST에는 실리지 않는다. secure 속성은 base URL이 https일 때만 켜므로 로컬 http 개발에서도 쿠키가 동작한다.</p>
 */
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

	/**
	 * 두 쿠키를 즉시 만료시킨다. 브라우저가 쿠키를 지우려면 설정할 때와 경로가 같아야 하므로
	 * refresh 쿠키는 여기서도 {@link #authPath}를 쓴다. 경로가 어긋나면 지운 것처럼 보여도 쿠키가 남는다.
	 */
	public void clear(HttpServletRequest request, HttpServletResponse response) {
		add(response, ACCESS_COOKIE, "", "/", Duration.ZERO);
		add(response, REFRESH_COOKIE, "", authPath(request), Duration.ZERO);
	}

	/**
	 * refresh 쿠키의 전송 범위. 컨텍스트 경로를 앞에 붙여야 서브 경로에 배포된 환경에서도 일치한다.
	 */
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

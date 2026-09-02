package link.srrrg.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.common.ApiErrorResponse;
import lombok.RequiredArgsConstructor;

/**
 * 브라우저 세션의 갱신과 종료를 처리한다. 세 엔드포인트 모두 쿠키의 refresh token만으로 동작하며,
 * access token이 이미 만료된 상태에서도 호출돼야 하므로 인가 규칙에서 인증을 면제받는다
 * ({@code SecurityConfiguration}의 {@code /api/web/auth/**}).
 *
 * <p>응답에 토큰을 담지 않는다. 새 토큰은 항상 {@code Set-Cookie}로만 나가므로 스크립트가 값을 읽을 수 없다.</p>
 */
@RestController
@RequestMapping("/api/web/auth")
@RequiredArgsConstructor
public class AuthController {

	private final WebSessionService sessionService;
	private final RefreshTokenService refreshTokenService;
	private final WebTokenCookies tokenCookies;

	/**
	 * refresh token을 회전시키고 새 토큰 쌍을 쿠키로 내려보낸다.
	 * 쿠키가 없으면 {@code null}이 그대로 서비스로 내려가 형식 검증에서 401로 끝난다.
	 */
	@PostMapping("/refresh")
	public ResponseEntity<Void> refresh(
			@CookieValue(name = WebTokenCookies.REFRESH_COOKIE, required = false) String refreshToken,
			HttpServletRequest request,
			HttpServletResponse response) {
		tokenCookies.write(request, response, sessionService.refresh(refreshToken));
		return ResponseEntity.noContent().build();
	}

	/**
	 * 이 브라우저의 세션을 끝낸다. 토큰이 이미 무효여도 성공으로 처리하는 것이 중요하다.
	 * 로그아웃 요청에 401을 돌려주면 사용자는 쿠키가 남은 채로 로그아웃하지 못한 상태에 갇힌다.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = WebTokenCookies.REFRESH_COOKIE, required = false) String refreshToken,
			HttpServletRequest request,
			HttpServletResponse response) {
		if (refreshToken != null) {
			try {
				refreshTokenService.logoutCurrent(refreshToken);
			} catch (InvalidRefreshTokenException ignored) {
				// 로그아웃은 token 상태와 무관하게 현재 브라우저 cookie를 제거한다.
			}
		}
		tokenCookies.clear(request, response);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 모든 기기의 세션을 끝낸다. 로그아웃과 달리 유효한 토큰을 요구한다.
	 * 남의 세션까지 끊는 동작이라 현재 세션의 소유자임이 확인돼야 하기 때문이다.
	 */
	@PostMapping("/logout-all")
	public ResponseEntity<Void> logoutAll(
			@CookieValue(name = WebTokenCookies.REFRESH_COOKIE, required = false) String refreshToken,
			HttpServletRequest request,
			HttpServletResponse response) {
		refreshTokenService.logoutAll(refreshToken);
		tokenCookies.clear(request, response);
		return ResponseEntity.noContent().build();
	}

	/**
	 * refresh 실패를 401로 바꾸면서 쿠키까지 지운다. 쓸 수 없는 토큰을 브라우저에 남겨 두면
	 * 화면이 갱신을 계속 재시도하게 되므로, 오류 응답과 함께 세션 흔적을 정리한다.
	 * 재사용 감지와 단순 무효를 같은 응답으로 합쳐 어느 쪽인지 알려주지 않는다.
	 */
	@ExceptionHandler({InvalidRefreshTokenException.class, RefreshTokenReuseException.class})
	public ResponseEntity<ApiErrorResponse> invalidRefresh(HttpServletRequest request, HttpServletResponse response) {
		tokenCookies.clear(request, response);
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiErrorResponse("INVALID_REFRESH_TOKEN", "refresh token이 유효하지 않습니다."));
	}
}

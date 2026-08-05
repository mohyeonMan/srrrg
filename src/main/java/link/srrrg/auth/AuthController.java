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

@RestController
@RequestMapping("/api/web/auth")
@RequiredArgsConstructor
public class AuthController {

	private final WebSessionService sessionService;
	private final RefreshTokenService refreshTokenService;
	private final WebTokenCookies tokenCookies;

	@PostMapping("/refresh")
	public ResponseEntity<Void> refresh(
			@CookieValue(name = WebTokenCookies.REFRESH_COOKIE, required = false) String refreshToken,
			HttpServletRequest request,
			HttpServletResponse response) {
		tokenCookies.write(request, response, sessionService.refresh(refreshToken));
		return ResponseEntity.noContent().build();
	}

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

	@PostMapping("/logout-all")
	public ResponseEntity<Void> logoutAll(
			@CookieValue(name = WebTokenCookies.REFRESH_COOKIE, required = false) String refreshToken,
			HttpServletRequest request,
			HttpServletResponse response) {
		refreshTokenService.logoutAll(refreshToken);
		tokenCookies.clear(request, response);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler({InvalidRefreshTokenException.class, RefreshTokenReuseException.class})
	public ResponseEntity<ApiErrorResponse> invalidRefresh(HttpServletRequest request, HttpServletResponse response) {
		tokenCookies.clear(request, response);
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiErrorResponse("INVALID_REFRESH_TOKEN", "refresh token이 유효하지 않습니다."));
	}
}

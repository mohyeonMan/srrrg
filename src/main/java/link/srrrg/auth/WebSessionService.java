package link.srrrg.auth;

import org.springframework.stereotype.Service;

import link.srrrg.auth.RefreshTokenService.RotatedRefreshToken;
import link.srrrg.identity.User;
import lombok.RequiredArgsConstructor;

/**
 * 짧게 만료되는 access token과 취소 가능한 refresh token을 한 쌍으로 묶어 웹 세션을 구성한다.
 * 로그인 성공 처리와 토큰 갱신 엔드포인트가 세션을 만드는 유일한 경로다.
 *
 * <p>두 토큰을 만들기 전에 {@link JwtService#requireConfigured()}를 먼저 호출한다.
 * 서명 키나 HTTPS 설정이 잘못된 상태에서 refresh token만 DB에 남고 access token 발급이 실패하면
 * 쓸 수 없는 세션 행이 쌓이기 때문이다.</p>
 */
@Service
@RequiredArgsConstructor
public class WebSessionService {

	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	public SessionTokens issue(User user) {
		jwtService.requireConfigured();
		String access = jwtService.issue(user.getId());
		String refresh = refreshTokenService.issue(user).rawToken();
		return new SessionTokens(access, refresh);
	}

	/**
	 * 회전된 refresh token과 새 access token을 함께 돌려준다.
	 * 회전에 실패하면 예외가 그대로 올라가 새 access token은 발급되지 않는다.
	 */
	public SessionTokens refresh(String rawRefreshToken) {
		jwtService.requireConfigured();
		RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
		return new SessionTokens(jwtService.issue(rotated.user().getId()), rotated.rawToken());
	}

	public record SessionTokens(String accessToken, String refreshToken) {
	}
}

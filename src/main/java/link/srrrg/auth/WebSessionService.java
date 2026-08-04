package link.srrrg.auth;

import org.springframework.stereotype.Service;

import link.srrrg.auth.RefreshTokenService.RotatedRefreshToken;
import link.srrrg.identity.User;
import lombok.RequiredArgsConstructor;

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

	public SessionTokens refresh(String rawRefreshToken) {
		jwtService.requireConfigured();
		RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
		return new SessionTokens(jwtService.issue(rotated.user().getId()), rotated.rawToken());
	}

	public record SessionTokens(String accessToken, String refreshToken) {
	}
}

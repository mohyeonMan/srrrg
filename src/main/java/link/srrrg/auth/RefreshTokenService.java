package link.srrrg.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private static final String URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	private static final Duration LIFETIME = Duration.ofDays(30);

	private final RefreshTokenRepository repository;
	private final SecureRandomStringGenerator random;

	@Transactional
	public IssuedRefreshToken issue(User user) {
		return create(user, UUID.randomUUID());
	}

	@Transactional(noRollbackFor = RefreshTokenReuseException.class)
	public RotatedRefreshToken rotate(String rawToken) {
		validateFormat(rawToken);
		RefreshToken current = repository.findByHashForUpdate(TokenHash.sha256(rawToken))
				.orElseThrow(InvalidRefreshTokenException::new);
		Instant now = Instant.now();
		if (current.getUsedAt() != null) {
			repository.revokeFamily(current.getTokenFamilyId(), now);
			throw new RefreshTokenReuseException();
		}
		if (current.getRevokedAt() != null || current.expiredAt(now)) {
			throw new InvalidRefreshTokenException();
		}
		IssuedRefreshToken replacement = create(current.getUser(), current.getTokenFamilyId());
		current.replaceWith(replacement.id(), now);
		return new RotatedRefreshToken(current.getUser(), replacement.rawToken());
	}

	@Transactional
	public void logoutCurrent(String rawToken) {
		RefreshToken token = find(rawToken);
		repository.revokeFamily(token.getTokenFamilyId(), Instant.now());
	}

	@Transactional
	public void logoutAll(String rawToken) {
		RefreshToken token = find(rawToken);
		if (token.getUsedAt() != null || token.getRevokedAt() != null || token.expiredAt(Instant.now())) {
			throw new InvalidRefreshTokenException();
		}
		repository.revokeAllForUser(token.getUser().getId(), Instant.now());
	}

	private RefreshToken find(String rawToken) {
		validateFormat(rawToken);
		return repository.findByHashForUpdate(TokenHash.sha256(rawToken))
				.orElseThrow(InvalidRefreshTokenException::new);
	}

	private IssuedRefreshToken create(User user, UUID familyId) {
		String raw = "srrrg_rt_" + random.generate(URL_SAFE, 43);
		RefreshToken saved = repository.saveAndFlush(RefreshToken.create(
				user, TokenHash.sha256(raw), familyId, Instant.now().plus(LIFETIME)));
		return new IssuedRefreshToken(saved.getId(), raw);
	}

	private void validateFormat(String rawToken) {
		if (rawToken == null || !rawToken.matches("srrrg_rt_[A-Za-z0-9_-]{43}")) {
			throw new InvalidRefreshTokenException();
		}
	}

	public record IssuedRefreshToken(Long id, String rawToken) {
	}

	public record RotatedRefreshToken(User user, String rawToken) {
	}
}

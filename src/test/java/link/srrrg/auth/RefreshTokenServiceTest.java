package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;

class RefreshTokenServiceTest {

	@Test
	void revokesWholeFamilyWhenUsedTokenIsPresentedAgain() {
		RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
		SecureRandomStringGenerator random = mock(SecureRandomStringGenerator.class);
		RefreshTokenService service = new RefreshTokenService(repository, random);
		String raw = "srrrg_rt_" + "a".repeat(43);
		UUID family = UUID.randomUUID();
		RefreshToken used = RefreshToken.create(
				User.create("user@example.com", "User"), TokenHash.sha256(raw), family, Instant.now().plusSeconds(60));
		used.replaceWith(2L, Instant.now());
		when(repository.findByHashForUpdate(TokenHash.sha256(raw))).thenReturn(Optional.of(used));

		assertThatThrownBy(() -> service.rotate(raw))
				.isInstanceOf(RefreshTokenReuseException.class);
		verify(repository).revokeFamily(org.mockito.ArgumentMatchers.eq(family),
				org.mockito.ArgumentMatchers.any(Instant.class));
	}

	@Test
	void rejectsMalformedTokenBeforeDatabaseLookup() {
		RefreshTokenService service = new RefreshTokenService(
				mock(RefreshTokenRepository.class), mock(SecureRandomStringGenerator.class));

		assertThatThrownBy(() -> service.rotate("not-a-refresh-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}
}

package link.srrrg.identity.account.service;

import link.srrrg.identity.account.model.AccountProfile;
import link.srrrg.identity.account.model.User;
import link.srrrg.identity.account.repository.UserRepository;
import link.srrrg.identity.account.service.AccountService;
import link.srrrg.identity.connection.model.OAuthAccount;
import link.srrrg.identity.connection.model.OAuthProvider;
import link.srrrg.identity.connection.repository.OAuthAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AccountServiceTest {
	private final UserRepository users = mock(UserRepository.class);
	private final OAuthAccountRepository accounts = mock(OAuthAccountRepository.class);
	private final AccountService service = new AccountService(users, accounts);

	@Test
	void trimsAndUpdatesDisplayName() {
		User user = mock(User.class);
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(1L);
		when(accounts.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
		when(user.getDisplayName()).thenReturn("새 이름");

		AccountProfile profile = service.updateDisplayName(1L, "  새 이름  ");

		verify(user).updateDisplayName("새 이름");
		assertEquals("새 이름", profile.displayName());
	}

	@Test
	void completesFirstLoginProfileWithoutTouchingEmail() {
		User user = User.create(null, "srrrg 사용자");
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(accounts.findByUserIdOrderByCreatedAtAsc(null)).thenReturn(List.of());

		service.completeOnboarding(1L, "  첫 사용자  ");

		assertEquals("첫 사용자", user.getDisplayName());
		assertNull(user.getEmail());
		assertEquals(false, user.needsOnboarding());
	}

	@Test
	void accountProfileExposesOnlyConnectedProviderNamesInCreationOrder() {
		User user = mock(User.class);
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(1L);
		OAuthAccount google = mock(OAuthAccount.class);
		OAuthAccount github = mock(OAuthAccount.class);
		when(google.getProvider()).thenReturn(OAuthProvider.GOOGLE);
		when(github.getProvider()).thenReturn(OAuthProvider.GITHUB);
		when(accounts.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(google, github));

		AccountProfile profile = service.get(1L);

		assertEquals(List.of("GOOGLE", "GITHUB"), profile.providers());
	}
}

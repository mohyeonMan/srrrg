package link.srrrg.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import link.srrrg.auth.SrrrgPrincipal;

class AccountControllerTest {

	@Test
	void trimsAndUpdatesDisplayName() {
		UserRepository users = mock(UserRepository.class);
		OAuthAccountRepository accounts = mock(OAuthAccountRepository.class);
		User user = mock(User.class);
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(user.getId()).thenReturn(1L);
		when(accounts.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
		when(user.getDisplayName()).thenReturn("새 이름");

		AccountController.AccountResponse response = new AccountController(users, accounts).update(
				new SrrrgPrincipal(1L), new AccountController.UpdateAccountRequest("  새 이름  "));

		verify(user).updateDisplayName("새 이름");
		assertEquals("새 이름", response.displayName());
	}

	@Test
	void completesFirstLoginProfile() {
		UserRepository users = mock(UserRepository.class);
		OAuthAccountRepository accounts = mock(OAuthAccountRepository.class);
		User user = User.create(null, "srrrg 사용자");
		when(users.findById(1L)).thenReturn(Optional.of(user));
		when(accounts.findByUserIdOrderByCreatedAtAsc(null)).thenReturn(List.of());

		new AccountController(users, accounts).completeOnboarding(new SrrrgPrincipal(1L),
				new AccountController.CompleteOnboardingRequest("  첫 사용자  ", " USER@Example.COM "));

		assertEquals("첫 사용자", user.getDisplayName());
		assertEquals("user@example.com", user.getEmail());
		assertEquals(false, user.needsOnboarding());
	}
}

package link.srrrg.auth.login.handler;

import link.srrrg.auth.login.handler.OAuthLoginSuccessHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import link.srrrg.identity.account.model.User;

class OAuthLoginSuccessHandlerTest {

	@Test
	void requiresProfileOnlyUntilFirstLoginSetupIsComplete() {
		User user = User.create("user@example.com", "OAuth User");
		String onboarding = OAuthLoginSuccessHandler.destination(user, "/projects?view=home");

		assertTrue(onboarding.startsWith("/onboarding?returnTo="));
		user.completeOnboarding("사용자");
		assertEquals("/projects?view=home", OAuthLoginSuccessHandler.destination(user, "/projects?view=home"));
	}
}

package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthProvider;

class OAuthProfileFactoryTest {

	@Test
	void convertsProviderProfileToInternalIdentity() {
		Map<String, Object> attributes = Map.of(
				"sub", "provider-user",
				ProviderOAuth2UserService.PROVIDER_EMAIL, "User@Example.com",
				ProviderOAuth2UserService.PROVIDER_EMAIL_VERIFIED, true,
				ProviderOAuth2UserService.DISPLAY_NAME, "Srrrg User");
		DefaultOAuth2User principal = new DefaultOAuth2User(
				List.of(new OAuth2UserAuthority(attributes)), attributes, "sub");
		OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
				principal, principal.getAuthorities(), "google");

		OAuthIdentity identity = OAuthProfileFactory.from(authentication);

		assertThat(identity.provider()).isEqualTo(OAuthProvider.GOOGLE);
		assertThat(identity.providerUserId()).isEqualTo("provider-user");
		assertThat(identity.providerEmail()).isEqualTo("User@Example.com");
		assertThat(identity.providerEmailVerified()).isTrue();
	}
}

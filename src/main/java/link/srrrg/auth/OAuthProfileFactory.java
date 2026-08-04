package link.srrrg.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthProvider;

final class OAuthProfileFactory {

	private OAuthProfileFactory() {
	}

	static OAuthIdentity from(OAuth2AuthenticationToken authentication) {
		return new OAuthIdentity(
				OAuthProvider.fromRegistrationId(authentication.getAuthorizedClientRegistrationId()),
				authentication.getName(),
				authentication.getPrincipal().getAttribute(ProviderOAuth2UserService.PROVIDER_EMAIL),
				Boolean.TRUE.equals(authentication.getPrincipal().getAttribute(
						ProviderOAuth2UserService.PROVIDER_EMAIL_VERIFIED)),
				authentication.getPrincipal().getAttribute(ProviderOAuth2UserService.DISPLAY_NAME));
	}
}

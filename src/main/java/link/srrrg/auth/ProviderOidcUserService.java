package link.srrrg.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
class ProviderOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final OidcUserService delegate = new OidcUserService();

	@Override
	public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
		OidcUser loaded = delegate.loadUser(request);
		Map<String, Object> attributes = new HashMap<>(loaded.getAttributes());
		ProviderProfile profile = ProviderProfile.google(attributes);
		if (profile.email() != null) {
			attributes.put(ProviderOAuth2UserService.PROVIDER_EMAIL, profile.email());
		}
		attributes.put(ProviderOAuth2UserService.PROVIDER_EMAIL_VERIFIED, profile.emailVerified());
		if (profile.displayName() != null) {
			attributes.put(ProviderOAuth2UserService.DISPLAY_NAME, profile.displayName());
		}
		return new DefaultOidcUser(loaded.getAuthorities(), loaded.getIdToken(),
				new OidcUserInfo(attributes), "sub");
	}
}

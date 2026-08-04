package link.srrrg.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProviderOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	static final String PROVIDER_EMAIL = "_srrrg_provider_email";
	static final String PROVIDER_EMAIL_VERIFIED = "_srrrg_provider_email_verified";
	static final String DISPLAY_NAME = "_srrrg_display_name";

	private final GitHubEmailClient gitHubEmailClient;
	private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

	@Override
	public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
		OAuth2User loaded = delegate.loadUser(request);
		String registrationId = request.getClientRegistration().getRegistrationId();
		Map<String, Object> attributes = new HashMap<>(loaded.getAttributes());
		ProviderProfile profile = switch (registrationId) {
			case "google" -> ProviderProfile.google(attributes);
			case "kakao" -> ProviderProfile.kakao(attributes);
			case "github" -> ProviderProfile.github(attributes,
					gitHubEmailClient.verifiedEmail(request.getAccessToken().getTokenValue()));
			default -> throw new IllegalArgumentException("지원하지 않는 OAuth 공급자입니다.");
		};
		if (profile.email() != null) {
			attributes.put(PROVIDER_EMAIL, profile.email());
		}
		attributes.put(PROVIDER_EMAIL_VERIFIED, profile.emailVerified());
		if (profile.displayName() != null) {
			attributes.put(DISPLAY_NAME, profile.displayName());
		}
		return new DefaultOAuth2User(loaded.getAuthorities(), attributes,
				request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
	}
}

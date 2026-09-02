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

/**
 * OIDC 로그인(Google)의 사용자 정보를 {@code ProviderOAuth2UserService}와 같은 속성 키로 맞춘다.
 * Spring Security가 OIDC와 일반 OAuth2를 다른 서비스로 처리하기 때문에 두 경로가 나뉘지만,
 * 뒤쪽 로직이 같은 키만 보면 되도록 결과 형태는 일치시킨다.
 */
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

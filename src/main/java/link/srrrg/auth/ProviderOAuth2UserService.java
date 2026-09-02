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
/**
 * 공급자마다 다른 사용자 정보 응답을 하나의 형태로 맞춰 준다.
 *
 * <p>Google, Kakao, GitHub는 이메일과 이름을 서로 다른 키와 중첩 구조로 돌려주고,
 * 이메일 검증 여부를 알려주는 방식도 제각각이다. 뒤쪽 로직이 공급자별 분기로 번지지 않도록
 * 여기서 표준 속성 키에 담아 넘긴다.</p>
 *
 * <p>속성 키에 접두사를 붙인 것은 공급자가 같은 이름의 속성을 보내 우리가 판단한 값을 덮어쓰는 것을 막기 위해서다.</p>
 *
 * <p>OIDC를 쓰는 Google은 이 서비스가 아니라 {@code ProviderOidcUserService}를 탄다.</p>
 */
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
				// GitHub는 사용자 정보 응답의 email이 공개 설정에 따라 비어 있거나 검증되지 않은 값일 수 있어
				// 별도 API로 검증된 기본 이메일을 따로 확인한다.
			case "github" -> ProviderProfile.github(attributes,
					gitHubEmailClient.verifiedEmail(request.getAccessToken().getTokenValue()));
				// 등록되지 않은 공급자가 여기 도달하면 이메일 검증 규칙이 정의되지 않은 채 로그인이 진행된다.
				// 새 공급자를 추가할 때 이 분기와 ProviderProfile을 함께 늘려야 한다.
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

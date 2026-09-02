package link.srrrg.auth;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;

/**
 * 설정에 clientId와 clientSecret이 모두 들어온 공급자만 등록한다.
 *
 * <p>미설정 공급자를 빼는 것이 의도다. 자격증명 없이 등록해 두면 로그인 화면에는 버튼이 보이는데
 * 실제로 누르면 공급자 오류로 끝난다. 여기서 빠지면 인가 엔드포인트 자체가 존재하지 않는다.</p>
 *
 * <p>Google과 GitHub는 Spring이 제공하는 기본값을 쓰고, Kakao는 표준 프리셋이 없어 엔드포인트를 직접 적는다.
 * Kakao가 {@code CLIENT_SECRET_POST}를 쓰는 것도 기본값과 다른 부분이다.</p>
 */
final class ConfiguredClientRegistrationRepository
		implements ClientRegistrationRepository, Iterable<ClientRegistration> {

	private final Map<String, ClientRegistration> registrations;
	private final String baseUrl;

	ConfiguredClientRegistrationRepository(OAuthClientProperties properties, String baseUrl) {
		// 끝의 슬래시를 떼지 않으면 리다이렉트 URI에 슬래시가 겹쳐, 공급자에 등록한 주소와 달라져 로그인이 거부된다.
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		Map<String, ClientRegistration> configured = new LinkedHashMap<>();
		add(configured, "google", properties.google(), this::google);
		add(configured, "kakao", properties.kakao(), this::kakao);
		add(configured, "github", properties.github(), this::github);
		this.registrations = Map.copyOf(configured);
	}

	@Override
	public ClientRegistration findByRegistrationId(String registrationId) {
		return registrations.get(registrationId);
	}

	@Override
	public Iterator<ClientRegistration> iterator() {
		return registrations.values().iterator();
	}

	private void add(Map<String, ClientRegistration> target, String id,
			OAuthClientProperties.Provider provider, RegistrationFactory factory) {
		if (provider != null && provider.configured()) {
			target.put(id, factory.create(provider));
		}
	}

	private ClientRegistration google(OAuthClientProperties.Provider provider) {
		return CommonOAuth2Provider.GOOGLE.getBuilder("google")
				.clientId(provider.clientId())
				.clientSecret(provider.clientSecret())
				.redirectUri(baseUrl + "/login/oauth2/code/{registrationId}")
				.scope("openid", "profile", "email")
				.build();
	}

	/**
	 * {@code user:email} 범위를 함께 요청한다. 이 권한이 없으면 검증된 이메일을 확인하는
	 * {@link GitHubEmailClient} 호출이 실패해 모든 GitHub 로그인이 미검증으로 처리된다.
	 */
	private ClientRegistration github(OAuthClientProperties.Provider provider) {
		return CommonOAuth2Provider.GITHUB.getBuilder("github")
				.clientId(provider.clientId())
				.clientSecret(provider.clientSecret())
				.redirectUri(baseUrl + "/login/oauth2/code/{registrationId}")
				.scope("read:user", "user:email")
				.build();
	}

	/**
	 * Kakao는 {@code profile_nickname}만 요청한다. 이메일은 별도 동의 항목이라 여기 범위에 없으면
	 * 사용자 정보 응답에도 오지 않고, 그 경우 이메일 없는 계정으로 가입된다.
	 * 사용자 식별 속성이 {@code id}인 것도 다른 공급자와 다르다.
	 */
	private ClientRegistration kakao(OAuthClientProperties.Provider provider) {
		return ClientRegistration.withRegistrationId("kakao")
				.clientId(provider.clientId())
				.clientSecret(provider.clientSecret())
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri(baseUrl + "/login/oauth2/code/{registrationId}")
				.scope("profile_nickname")
				.authorizationUri("https://kauth.kakao.com/oauth/authorize")
				.tokenUri("https://kauth.kakao.com/oauth/token")
				.userInfoUri("https://kapi.kakao.com/v2/user/me")
				.userNameAttributeName("id")
				.clientName("Kakao")
				.build();
	}

	@FunctionalInterface
	private interface RegistrationFactory {
		ClientRegistration create(OAuthClientProperties.Provider provider);
	}
}

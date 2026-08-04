package link.srrrg.auth;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;

final class ConfiguredClientRegistrationRepository
		implements ClientRegistrationRepository, Iterable<ClientRegistration> {

	private final Map<String, ClientRegistration> registrations;
	private final String baseUrl;

	ConfiguredClientRegistrationRepository(OAuthClientProperties properties, String baseUrl) {
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

	private ClientRegistration github(OAuthClientProperties.Provider provider) {
		return CommonOAuth2Provider.GITHUB.getBuilder("github")
				.clientId(provider.clientId())
				.clientSecret(provider.clientSecret())
				.redirectUri(baseUrl + "/login/oauth2/code/{registrationId}")
				.scope("read:user", "user:email")
				.build();
	}

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

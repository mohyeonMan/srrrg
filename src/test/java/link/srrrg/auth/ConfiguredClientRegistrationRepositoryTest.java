package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class ConfiguredClientRegistrationRepositoryTest {

	@Test
	void configuresOnlyProvidersWithCredentialsAndRequiredScopes() {
		OAuthClientProperties.Provider configured = new OAuthClientProperties.Provider("client", "secret");
		OAuthClientProperties.Provider empty = new OAuthClientProperties.Provider("", "");
		ConfiguredClientRegistrationRepository repository = new ConfiguredClientRegistrationRepository(
				new OAuthClientProperties(configured, configured, empty), "https://srrrg.link");

		ClientRegistration google = repository.findByRegistrationId("google");
		ClientRegistration kakao = repository.findByRegistrationId("kakao");
		assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
		assertThat(kakao.getScopes()).containsExactlyInAnyOrder("profile_nickname", "account_email");
		assertThat(google.getRedirectUri()).isEqualTo("https://srrrg.link/login/oauth2/code/{registrationId}");
		assertThat(repository.findByRegistrationId("github")).isNull();
	}
}

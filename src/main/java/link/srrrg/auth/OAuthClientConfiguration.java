package link.srrrg.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
class OAuthClientConfiguration {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(
			OAuthClientProperties properties,
			@Value("${srrrg.base-url}") String baseUrl) {
		return new ConfiguredClientRegistrationRepository(properties, baseUrl);
	}

	@Bean
	OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository registrations) {
		return new InMemoryOAuth2AuthorizedClientService(registrations);
	}
}

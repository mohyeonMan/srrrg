package link.srrrg.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
/**
 * OAuth 클라이언트 관련 빈을 등록한다.
 *
 * <p>인가된 클라이언트 저장소가 파드 로컬 메모리인 것은 의도된 선택이다. 이 서비스는 로그인 직후
 * 공급자 토큰을 버리고 자체 세션으로만 동작하므로 파드 사이에 공유할 필요가 없다.
 * 공급자 API를 계속 호출해야 하는 기능이 생기면 그때는 공유 저장소가 필요하다.</p>
 */
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

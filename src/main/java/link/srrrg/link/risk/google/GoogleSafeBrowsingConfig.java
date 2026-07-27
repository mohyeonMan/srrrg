package link.srrrg.link.risk.google;

import java.net.URI;
import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Configuration
@ConditionalOnProperty(
		prefix = "srrrg.url-risk",
		name = "provider",
		havingValue = "google",
		matchIfMissing = true
)
@EnableConfigurationProperties(GoogleSafeBrowsingProperties.class)
@Slf4j
class GoogleSafeBrowsingConfig {

	@Bean
	RestClient safeBrowsingRestClient(GoogleSafeBrowsingProperties properties) {
		log.info("Configuring Google Safe Browsing client: endpointHost={}, connectTimeout={}, readTimeout={}, apiKeyConfigured={}",
				endpointHost(properties.endpoint()), properties.connectTimeout(), properties.readTimeout(),
				properties.apiKey() != null && !properties.apiKey().isBlank());
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().requestFactory(requestFactory).build();
	}

	private String endpointHost(String endpoint) {
		try {
			return URI.create(endpoint).getHost();
		} catch (IllegalArgumentException exception) {
			return "invalid";
		}
	}
}

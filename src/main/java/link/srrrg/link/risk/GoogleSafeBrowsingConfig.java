package link.srrrg.link.risk;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(GoogleSafeBrowsingProperties.class)
@Slf4j
class GoogleSafeBrowsingConfig {

	@Bean
	RestClient safeBrowsingRestClient(GoogleSafeBrowsingProperties properties) {
		// API 키 값은 제외하고 연결 설정 여부와 타임아웃만 로그에 남김.
		log.info("Configuring Google Safe Browsing client: endpointHost={}, connectTimeout={}, readTimeout={}, apiKeyConfigured={}",
				endpointHost(properties.endpoint()), properties.connectTimeout(), properties.readTimeout(),
				properties.apiKey() != null && !properties.apiKey().isBlank());
		// 연결 타임아웃과 응답 읽기 타임아웃을 각각 적용함.
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().requestFactory(requestFactory).build();
	}

	private String endpointHost(String endpoint) {
		try {
			return java.net.URI.create(endpoint).getHost();
		} catch (IllegalArgumentException exception) {
			return "invalid";
		}
	}
}

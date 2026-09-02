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

/**
 * Safe Browsing 호출용 {@code RestClient}를 구성한다.
 *
 * <p>연결과 읽기 타임아웃을 명시하는 것이 핵심이다. 이 호출은 링크 생성과 리다이렉트 경로에서
 * 동기로 일어나므로, 타임아웃이 없으면 외부 서비스가 느려질 때 요청 스레드가 함께 묶인다.
 * 타임아웃으로 끝난 검사는 UNKNOWN이 되어 fail-closed 정책에 따라 거부된다.</p>
 *
 * <p>기동 로그에 API key 설정 여부만 남기고 값은 남기지 않는다.</p>
 */
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

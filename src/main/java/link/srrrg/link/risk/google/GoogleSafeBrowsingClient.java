package link.srrrg.link.risk.google;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import io.micrometer.core.instrument.Timer;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.link.risk.RiskVerdict;
import link.srrrg.link.risk.UrlRiskAssessment;
import link.srrrg.link.risk.UrlRiskChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Google Safe Browsing v5로 URL을 검사한다. 통신 또는 응답 처리 실패는 UNKNOWN으로 반환한다.
 */
@Component
@ConditionalOnProperty(
		prefix = "srrrg.url-risk",
		name = "provider",
		havingValue = "google",
		matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class GoogleSafeBrowsingClient implements UrlRiskChecker {

	private static final MediaType PROTOBUF_MEDIA_TYPE = MediaType.parseMediaType("application/x-protobuf");

	private final RestClient restClient;
	private final GoogleSafeBrowsingProperties properties;
	private final SrrrgMetrics metrics;

	@Override
	public UrlRiskAssessment check(String url) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		Instant verifiedAt = Instant.now();
		String urlHost = hostOf(url);
		try {
			if (!StringUtils.hasText(properties.apiKey())) {
				log.warn("Safe Browsing check skipped: reason=API_KEY_NOT_CONFIGURED, urlHost={}", urlHost);
				outcome = "not_configured";
				return UrlRiskAssessment.unknown(verifiedAt);
			}

			URI requestUri = UriComponentsBuilder.fromUriString(properties.endpoint())
					.queryParam("key", properties.apiKey())
					.queryParam("urls", url)
					.build()
					.encode()
					.toUri();
			byte[] responseBody = restClient.get()
					.uri(requestUri)
					.accept(PROTOBUF_MEDIA_TYPE)
					.retrieve()
					.body(byte[].class);
			if (responseBody == null) {
				outcome = "invalid_response";
				return invalidResponse(verifiedAt, urlHost);
			}

			SafeBrowsingProtobufDecoder.Response response = SafeBrowsingProtobufDecoder.decode(responseBody);
			Duration cacheDuration = response.cacheDuration();
			if (cacheDuration == null || cacheDuration.isZero() || cacheDuration.isNegative()) {
				outcome = "invalid_response";
				return invalidResponse(verifiedAt, urlHost);
			}

			RiskVerdict verdict = response.threatCount() == 0
					? RiskVerdict.SAFE
					: RiskVerdict.THREAT;

			if (verdict == RiskVerdict.THREAT) {
				log.warn("Safe Browsing threat detected: matchCount={}, urlHost={}",
						response.threatCount(), urlHost);
			} else {
				log.debug("Safe Browsing check completed: verdict={}, cacheDuration={}, urlHost={}",
						verdict, cacheDuration, urlHost);
			}
			outcome = verdict == RiskVerdict.SAFE ? "safe" : "threat";
			return new UrlRiskAssessment(verdict, verifiedAt, verifiedAt.plus(cacheDuration));
		} catch (IOException exception) {
			outcome = "invalid_response";
			log.warn("Safe Browsing check failed: reason=INVALID_RESPONSE, urlHost={}", urlHost);
			return UrlRiskAssessment.unknown(verifiedAt);
		} catch (RestClientException exception) {
			outcome = isTimeout(exception) ? "timeout" : "error";
			log.warn("Safe Browsing check failed: errorType={}, urlHost={}",
					exception.getClass().getSimpleName(), urlHost);
			return UrlRiskAssessment.unknown(verifiedAt);
		} finally {
			metrics.recordUrlRiskCheck(sample, "google", outcome);
		}
	}

	private boolean isTimeout(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private UrlRiskAssessment invalidResponse(Instant verifiedAt, String urlHost) {
		log.warn("Safe Browsing check failed: reason=INVALID_RESPONSE, urlHost={}", urlHost);
		return UrlRiskAssessment.unknown(verifiedAt);
	}

	private String hostOf(String value) {
		try {
			String host = URI.create(value).getHost();
			return StringUtils.hasText(host) ? host : "unknown";
		} catch (IllegalArgumentException exception) {
			return "invalid";
		}
	}
}

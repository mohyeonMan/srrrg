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
/**
 * Google Safe Browsing v5로 URL을 검사한다. 통신 또는 응답 처리 실패는 UNKNOWN으로 반환한다.
 *
 * <p>모든 실패를 예외 대신 UNKNOWN으로 돌려주는 것이 이 클래스의 계약이다. 판정과 실패를 구분해
 * 호출자가 정책을 정하게 하려는 것이며, 현재 정책은 UNKNOWN을 거부로 취급하는 fail-closed다.
 * 여기서 예외를 던지도록 바꾸면 그 정책 판단이 호출자에서 사라진다.</p>
 *
 * <p>로그에는 URL 전체가 아니라 호스트만 남긴다. 검사 대상 URL에 쿼리로 개인정보나 토큰이
 * 들어 있을 수 있기 때문이다.</p>
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
			// API key가 없으면 검사를 건너뛴다. 이때 SAFE가 아니라 UNKNOWN을 돌려주는 것이 중요하다.
			// 설정 누락이 곧 검사 없는 통과가 되면 안 되므로, 호출자의 fail-closed 정책에 맡긴다.
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
			// 캐시 기간이 없으면 판정을 신뢰하지 않는다. 응답 형태가 예상과 다르다는 뜻이고,
			// 유효기간 없는 결과는 저장할 수도 없다.
			if (cacheDuration == null || cacheDuration.isZero() || cacheDuration.isNegative()) {
				outcome = "invalid_response";
				return invalidResponse(verifiedAt, urlHost);
			}

			// 위협 목록이 비어 있으면 안전으로 본다. 이 판정은 응답을 정상적으로 해석했을 때만 유효하며,
			// 해석에 실패한 경우는 위에서 이미 UNKNOWN으로 빠져나갔다.
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

	/**
	 * 원인 사슬을 따라가며 타임아웃인지 확인한다. 결과는 메트릭 태그를 가르는 데만 쓰인다.
	 * 타임아웃과 그 밖의 오류를 나눠야 외부 검사가 느린 것인지 실패하는 것인지 구분할 수 있다.
	 */
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

	/**
	 * 로그에 쓸 호스트만 뽑는다. 실패해도 예외를 내지 않고 대체 문자열을 돌려준다.
	 * 로그를 만들다 검사 자체가 실패하면 안 되기 때문이다.
	 */
	private String hostOf(String value) {
		try {
			String host = URI.create(value).getHost();
			return StringUtils.hasText(host) ? host : "unknown";
		} catch (IllegalArgumentException exception) {
			return "invalid";
		}
	}
}

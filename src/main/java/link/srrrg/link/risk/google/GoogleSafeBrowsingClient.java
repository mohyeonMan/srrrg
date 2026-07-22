package link.srrrg.link.risk.google;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import link.srrrg.link.risk.RiskVerdict;
import link.srrrg.link.risk.UrlRiskAssessment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleSafeBrowsingClient {

	private static final MediaType PROTOBUF_MEDIA_TYPE = MediaType.parseMediaType("application/x-protobuf");
	private static final Duration MAX_THREAT_CACHE_DURATION = Duration.ofMinutes(30);

	private final RestClient restClient;
	private final GoogleSafeBrowsingProperties properties;

	public UrlRiskAssessment check(String url) {
		Instant verifiedAt = Instant.now();
		String urlHost = hostOf(url);
		String urlId = urlId(url);
		if (!StringUtils.hasText(properties.apiKey())) {
			log.warn("Safe Browsing check skipped: reason=API_KEY_NOT_CONFIGURED, urlHost={}, urlId={}",
					urlHost, urlId);
			return UrlRiskAssessment.unknown(verifiedAt);
		}

		long startedAt = System.nanoTime();
		log.info("Safe Browsing request started: provider=google, endpointHost={}, urlHost={}, urlId={}",
				hostOf(properties.endpoint()), urlHost, urlId);
		try {
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
					.onStatus(HttpStatusCode::isError, (request, result) -> {
						throw new SafeBrowsingHttpException(result.getStatusCode());
					})
					.body(byte[].class);
			if (responseBody == null) {
				log.warn("Safe Browsing request failed: reason=INVALID_RESPONSE, elapsedMs={}, urlHost={}, urlId={}",
						elapsedMillis(startedAt), urlHost, urlId);
				return UrlRiskAssessment.unknown(verifiedAt);
			}

			SafeBrowsingProtobufDecoder.Response response = SafeBrowsingProtobufDecoder.decode(responseBody);
			Duration cacheDuration = response.cacheDuration();
			if (cacheDuration == null || cacheDuration.isZero() || cacheDuration.isNegative()) {
				log.warn("Safe Browsing request failed: reason=INVALID_RESPONSE, elapsedMs={}, urlHost={}, urlId={}",
						elapsedMillis(startedAt), urlHost, urlId);
				return UrlRiskAssessment.unknown(verifiedAt);
			}
			RiskVerdict verdict = response.threatCount() == 0
					? RiskVerdict.SAFE
					: RiskVerdict.THREAT;
			if (verdict == RiskVerdict.THREAT && cacheDuration.compareTo(MAX_THREAT_CACHE_DURATION) > 0) {
				cacheDuration = MAX_THREAT_CACHE_DURATION;
			}
			UrlRiskAssessment assessment = new UrlRiskAssessment(
					verdict, verifiedAt, verifiedAt.plus(cacheDuration));
			if (verdict == RiskVerdict.THREAT) {
				log.warn("Safe Browsing threat detected: elapsedMs={}, matchCount={}, urlHost={}, urlId={}",
						elapsedMillis(startedAt), response.threatCount(), urlHost, urlId);
			} else {
				log.info("Safe Browsing request completed: verdict={}, cacheDuration={}, elapsedMs={}, urlHost={}, urlId={}",
						verdict, cacheDuration, elapsedMillis(startedAt), urlHost, urlId);
			}
			return assessment;
		} catch (SafeBrowsingHttpException exception) {
			log.warn("Safe Browsing HTTP error: status={}, elapsedMs={}, urlHost={}, urlId={}",
					exception.status().value(), elapsedMillis(startedAt), urlHost, urlId);
			return UrlRiskAssessment.unknown(verifiedAt);
		} catch (RestClientException exception) {
			log.warn("Safe Browsing transport error: type={}, causeType={}, elapsedMs={}, urlHost={}, urlId={}",
					exception.getClass().getSimpleName(), causeType(exception), elapsedMillis(startedAt), urlHost, urlId);
			return UrlRiskAssessment.unknown(verifiedAt);
		} catch (IOException exception) {
			log.warn("Safe Browsing processing error: type={}, causeType={}, elapsedMs={}, urlHost={}, urlId={}",
					exception.getClass().getSimpleName(), causeType(exception), elapsedMillis(startedAt), urlHost, urlId);
			return UrlRiskAssessment.unknown(verifiedAt);
		} catch (RuntimeException exception) {
			log.warn("Safe Browsing processing error: type={}, causeType={}, elapsedMs={}, urlHost={}, urlId={}",
					exception.getClass().getSimpleName(), causeType(exception), elapsedMillis(startedAt), urlHost, urlId);
			return UrlRiskAssessment.unknown(verifiedAt);
		}
	}

	private long elapsedMillis(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
	}

	private String hostOf(String value) {
		try {
			String host = URI.create(value).getHost();
			return StringUtils.hasText(host) ? host : "unknown";
		} catch (IllegalArgumentException exception) {
			return "invalid";
		}
	}

	private String urlId(String url) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(url.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 6);
		} catch (NoSuchAlgorithmException exception) {
			return "unavailable";
		}
	}

	private String causeType(Throwable exception) {
		Throwable cause = exception.getCause();
		return cause == null ? "none" : cause.getClass().getSimpleName();
	}

	private static class SafeBrowsingHttpException extends RuntimeException {
		private final HttpStatusCode status;

		SafeBrowsingHttpException(HttpStatusCode status) {
			super("Safe Browsing HTTP status " + status.value());
			this.status = status;
		}

		HttpStatusCode status() {
			return status;
		}
	}
}

package link.srrrg.link.risk;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GoogleSafeBrowsingUrlRiskChecker implements UrlRiskChecker {

	private final RestClient restClient;
	private final GoogleSafeBrowsingProperties properties;

	public GoogleSafeBrowsingUrlRiskChecker(
			RestClient safeBrowsingRestClient,
			GoogleSafeBrowsingProperties properties
	) {
		this.restClient = safeBrowsingRestClient;
		this.properties = properties;
	}

	@Override
	public UrlRiskCheckResult check(String url) {
		// 전체 URL 대신 호스트와 해시 식별자만 로그에 남겨 쿼리 정보 노출을 막음.
		String urlHost = hostOf(url);
		String urlId = urlId(url);
		if (!StringUtils.hasText(properties.apiKey())) {
			log.warn("Safe Browsing check skipped: reason=API_KEY_NOT_CONFIGURED, urlHost={}, urlId={}",
					urlHost, urlId);
			return UrlRiskCheckResult.CHECK_FAILED;
		}

		long startedAt = System.nanoTime();
		log.info("Safe Browsing request started: provider=google, endpointHost={}, urlHost={}, urlId={}",
				hostOf(properties.endpoint()), urlHost, urlId);
		try {
			// Safe Browsing에는 scheme, host, port, path, query가 포함된 전체 URL을 전달함.
			SafeBrowsingResponse response = restClient.post()
					.uri(properties.endpoint() + "?key={apiKey}", properties.apiKey())
					.body(SafeBrowsingRequest.forUrl(url))
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, result) -> {
						throw new SafeBrowsingHttpException(result.getStatusCode());
					})
					.body(SafeBrowsingResponse.class);
			if (response == null) {
				log.warn("Safe Browsing request failed: reason=EMPTY_RESPONSE, elapsedMs={}, urlHost={}, urlId={}",
						elapsedMillis(startedAt), urlHost, urlId);
				return UrlRiskCheckResult.CHECK_FAILED;
			}
			// matches가 없거나 빈 배열이면 알려진 위협 미탐지로 판단함.
			int matchCount = response.matches() == null ? 0 : response.matches().size();
			UrlRiskCheckResult result = matchCount == 0
					? UrlRiskCheckResult.NO_THREAT_FOUND
					: UrlRiskCheckResult.THREAT_DETECTED;
			if (result == UrlRiskCheckResult.THREAT_DETECTED) {
				log.warn("Safe Browsing threat detected: elapsedMs={}, matchCount={}, urlHost={}, urlId={}",
						elapsedMillis(startedAt), matchCount, urlHost, urlId);
			} else {
				log.info("Safe Browsing request completed: result={}, elapsedMs={}, urlHost={}, urlId={}",
						result, elapsedMillis(startedAt), urlHost, urlId);
			}
			return result;
		} catch (SafeBrowsingHttpException exception) {
			// Google 오류 상세나 API 키는 응답과 로그에 노출하지 않음.
			log.warn("Safe Browsing HTTP error: status={}, elapsedMs={}, urlHost={}, urlId={}",
					exception.status().value(), elapsedMillis(startedAt), urlHost, urlId);
			return UrlRiskCheckResult.CHECK_FAILED;
		} catch (RestClientException exception) {
			log.warn("Safe Browsing transport error: type={}, causeType={}, elapsedMs={}, urlHost={}, urlId={}",
					exception.getClass().getSimpleName(), causeType(exception), elapsedMillis(startedAt), urlHost, urlId);
			return UrlRiskCheckResult.CHECK_FAILED;
		} catch (RuntimeException exception) {
			log.warn("Safe Browsing processing error: type={}, causeType={}, elapsedMs={}, urlHost={}, urlId={}",
					exception.getClass().getSimpleName(), causeType(exception), elapsedMillis(startedAt), urlHost, urlId);
			return UrlRiskCheckResult.CHECK_FAILED;
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
			// 같은 URL의 로그를 연결할 수 있도록 SHA-256 앞 6바이트만 사용함.
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

	private record SafeBrowsingRequest(Client client, ThreatInfo threatInfo) {
		static SafeBrowsingRequest forUrl(String url) {
			// 피싱, 악성코드, 원치 않는 소프트웨어 위협을 URL 단위로 요청함.
			return new SafeBrowsingRequest(
					new Client("srrrg", "1.0"),
					new ThreatInfo(
							List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE"),
							List.of("ANY_PLATFORM"),
							List.of("URL"),
							List.of(new ThreatEntry(url))
					)
			);
		}
	}

	private record Client(String clientId, String clientVersion) { }
	private record ThreatInfo(List<String> threatTypes, List<String> platformTypes,
			List<String> threatEntryTypes, List<ThreatEntry> threatEntries) { }
	private record ThreatEntry(String url) { }
	private record SafeBrowsingResponse(List<Object> matches) { }

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

package link.srrrg.link.risk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import link.srrrg.link.risk.google.GoogleSafeBrowsingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlRiskVerificationService {

	private final UrlVerificationRepository repository;
	private final GoogleSafeBrowsingClient safeBrowsingClient;
	private final MeterRegistry meterRegistry;
	private final ConcurrentHashMap<String, CompletableFuture<UrlRiskAssessment>> inFlight = new ConcurrentHashMap<>();

	public UrlRiskAssessment verify(String url) {
		String urlHash = hash(url);
		UrlRiskAssessment cached = findFresh(urlHash, url, Instant.now()).orElse(null);
		if (cached != null) {
			recordCacheRequest("hit");
			log.debug("URL risk verification cache hit: urlId={}, verdict={}, expiresAt={}",
					shortId(urlHash), cached.verdict(), cached.expiresAt());
			return cached;
		}
		recordCacheRequest("miss");
		return verifyOnce(urlHash, url);
	}

	private UrlRiskAssessment verifyOnce(String urlHash, String url) {
		CompletableFuture<UrlRiskAssessment> pending = new CompletableFuture<>();
		CompletableFuture<UrlRiskAssessment> existing = inFlight.putIfAbsent(urlHash, pending);
		if (existing != null) {
			meterRegistry.counter("srrrg.url.risk.inflight.joins").increment();
			log.debug("URL risk verification joined in-flight request: urlId={}", shortId(urlHash));
			return await(existing);
		}

		try {
			UrlRiskAssessment result = refresh(urlHash, url);
			pending.complete(result);
			return result;
		} catch (RuntimeException exception) {
			pending.completeExceptionally(exception);
			throw exception;
		} finally {
			inFlight.remove(urlHash, pending);
		}
	}

	private UrlRiskAssessment refresh(String urlHash, String url) {
		UrlRiskAssessment cached = findFresh(urlHash, url, Instant.now()).orElse(null);
		if (cached != null) {
			return cached;
		}

		Timer.Sample sample = Timer.start(meterRegistry);
		UrlRiskAssessment assessment = safeBrowsingClient.check(url);
		sample.stop(Timer.builder("srrrg.url.risk.provider.duration")
				.tag("verdict", assessment.verdict().name())
				.register(meterRegistry));
		meterRegistry.counter(
				"srrrg.url.risk.provider.requests",
				"verdict", assessment.verdict().name()
		).increment();
		if (assessment.isCacheable()) {
			repository.upsert(
					urlHash,
					url,
					assessment.verdict().name(),
					assessment.verifiedAt(),
					assessment.expiresAt()
			);
			log.info("URL risk verification cached: urlId={}, verdict={}, expiresAt={}",
					shortId(urlHash), assessment.verdict(), assessment.expiresAt());
		}
		return assessment;
	}

	private Optional<UrlRiskAssessment> findFresh(String urlHash, String url, Instant now) {
		return repository.findById(urlHash)
				.filter(verification -> verification.matches(url))
				.filter(verification -> verification.isFreshAt(now))
				.map(UrlVerification::toAssessment);
	}

	private UrlRiskAssessment await(CompletableFuture<UrlRiskAssessment> existing) {
		try {
			return existing.join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw exception;
		}
	}

	private String hash(String url) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(url.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String shortId(String urlHash) {
		return urlHash.substring(0, 12);
	}

	private void recordCacheRequest(String result) {
		meterRegistry.counter("srrrg.url.risk.cache.requests", "result", result).increment();
	}
}

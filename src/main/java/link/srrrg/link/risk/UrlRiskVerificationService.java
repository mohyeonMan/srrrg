package link.srrrg.link.risk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import link.srrrg.common.metrics.SrrrgMetrics;
import lombok.RequiredArgsConstructor;

/**
 * DB에 유효한 검증 결과가 있으면 재사용하고, 없거나 만료됐으면 Google Safe Browsing으로 다시 검사한다.
 *
 * <p>검사에 실패한 {@link RiskVerdict#UNKNOWN} 결과는 저장하지 않아 다음 요청에서 다시 검사한다.
 */
@Service
@RequiredArgsConstructor
public class UrlRiskVerificationService {

	private final UrlVerificationRepository repository;
	private final UrlRiskChecker urlRiskChecker;
	private final SrrrgMetrics metrics;

	public UrlRiskAssessment verify(String url) {
		String urlHash = hash(url);
		var cached = repository.findById(urlHash);
		if (cached.isEmpty()) {
			metrics.recordUrlRiskCache("miss_absent");
			return checkAndStore(urlHash, url);
		}

		UrlVerification verification = cached.get();
		if (!verification.matches(url)) {
			metrics.recordUrlRiskCache("miss_absent");
			return checkAndStore(urlHash, url);
		}
		if (!verification.isFreshAt(Instant.now())) {
			metrics.recordUrlRiskCache("miss_stale");
			return checkAndStore(urlHash, url);
		}

		metrics.recordUrlRiskCache("hit");
		return verification.toAssessment();
	}

	private UrlRiskAssessment checkAndStore(String urlHash, String url) {
		UrlRiskAssessment assessment = urlRiskChecker.check(url);
		if (assessment.isCacheable()) {
			repository.saveIfNewer(
					urlHash,
					url,
					assessment.verdict().name(),
					assessment.verifiedAt(),
					assessment.expiresAt()
			);
		}
		return assessment;
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
}

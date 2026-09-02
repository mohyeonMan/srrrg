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
/**
 * URL 위험 검사 결과를 DB에 캐시해 외부 호출을 줄인다. 링크 생성과 리다이렉트가 모두 이 경로를 지나므로,
 * 캐시가 없으면 인기 링크 하나가 매 요청마다 외부 호출을 일으킨다.
 *
 * <p>캐시를 Redis가 아니라 DB에 두는 것은 만료가 Google이 응답으로 내려준 기간에 따라 달라지고,
 * 검사 이력이 남아야 하기 때문이다. 모든 파드가 같은 표를 보므로 캐시도 파드 사이에 공유된다.</p>
 *
 * <p>{@link RiskVerdict#UNKNOWN}은 저장하지 않는다. 판정이 아니라 실패이므로 캐시해 두면
 * 검사 서비스가 복구된 뒤에도 만료될 때까지 실패 상태가 유지된다.</p>
 */
@Service
@RequiredArgsConstructor
public class UrlRiskVerificationService {

	private final UrlVerificationRepository repository;
	private final UrlRiskChecker urlRiskChecker;
	private final SrrrgMetrics metrics;

	/**
	 * 캐시를 먼저 보고 쓸 수 있으면 재사용, 아니면 외부 검사를 수행한다.
	 *
	 * <p>해시가 같아도 원본 URL을 다시 대조하는 것은 해시 충돌 대비다. 다른 URL의 판정을 물려받으면
	 * 위험한 주소가 안전으로 통과할 수 있어, 값이 다르면 캐시를 무시하고 새로 검사한다.</p>
	 *
	 * @return 판정과 유효기간. 외부 호출이 필요하면 그 시간만큼 블로킹되므로 트랜잭션 밖에서 호출한다
	 */
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

	/**
	 * 외부 검사를 수행하고 캐시 가능한 결과만 저장한다.
	 * 저장에 {@code saveIfNewer}를 쓰는 이유는 여러 파드가 같은 URL을 동시에 검사할 수 있어서다.
	 * 나중에 끝난 오래된 검사가 최신 판정을 덮어쓰지 않게 한다.
	 */
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

	/**
	 * URL을 캐시 키로 바꾼다. 길이가 2048자까지인 URL을 그대로 기본 키로 쓰지 않기 위한 것이며,
	 * 비밀값이 아니므로 보안 목적의 해시는 아니다.
	 */
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

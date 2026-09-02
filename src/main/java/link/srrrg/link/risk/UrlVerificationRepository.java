package link.srrrg.link.risk;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * URL 위험 검사 캐시를 다룬다. 여러 파드가 같은 URL을 동시에 검사하므로 단순 저장으로는 안 되고,
 * 아래 upsert가 그 경합을 처리한다.
 */
interface UrlVerificationRepository extends JpaRepository<UrlVerification, String> {

	@Transactional
	@Modifying
	@Query(value = """
			INSERT INTO url_verifications (
				url_hash, original_url, verdict, verified_at, expires_at
			) VALUES (
				:urlHash, :originalUrl, :verdict, :verifiedAt, :expiresAt
			)
			ON CONFLICT (url_hash) DO UPDATE SET
				original_url = EXCLUDED.original_url,
				verdict = EXCLUDED.verdict,
				verified_at = EXCLUDED.verified_at,
				expires_at = EXCLUDED.expires_at
			WHERE url_verifications.verified_at <= EXCLUDED.verified_at
			""", nativeQuery = true)
	/**
	 * 없으면 넣고 있으면 갱신하되, 기존 기록이 더 최신이면 아무것도 하지 않는다.
	 *
	 * <p>{@code ON CONFLICT}로 한 문장에 묶은 것은 조회 후 저장으로 나누면 그 사이에 다른 파드가
	 * 같은 키를 넣어 유일 제약 위반이 나기 때문이다. {@code WHERE} 절의 시각 비교는 늦게 끝난
	 * 오래된 검사가 새 판정을 되돌리는 것을 막는다. 이 조건이 없으면 방금 THREAT로 바뀐 URL이
	 * 먼저 시작된 SAFE 응답에 덮여 다시 통과할 수 있다.</p>
	 */
	void saveIfNewer(
			@Param("urlHash") String urlHash,
			@Param("originalUrl") String originalUrl,
			@Param("verdict") String verdict,
			@Param("verifiedAt") Instant verifiedAt,
			@Param("expiresAt") Instant expiresAt
	);
}

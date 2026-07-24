package link.srrrg.link.risk;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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
	void saveIfNewer(
			@Param("urlHash") String urlHash,
			@Param("originalUrl") String originalUrl,
			@Param("verdict") String verdict,
			@Param("verifiedAt") Instant verifiedAt,
			@Param("expiresAt") Instant expiresAt
	);
}

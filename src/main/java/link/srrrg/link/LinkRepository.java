package link.srrrg.link;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

	boolean existsByCode(String code);

	Optional<Link> findByCode(String code);

	@Modifying
	@Query("update Link l set l.clickCount = l.clickCount + 1 where l.code = :code")
	int incrementClickCountByCode(@Param("code") String code);

	@Modifying
	@Query("update Link l set l.redirectCount = l.redirectCount + 1 where l.code = :code")
	int incrementRedirectCountByCode(@Param("code") String code);

	@Modifying
	// 검사한 URL과 현재 저장 URL이 같을 때만 검사 결과를 반영함.
	@Query("""
			update Link l
			set l.status = :status, l.verifiedAt = :verifiedAt
			where l.code = :code and l.originalUrl = :originalUrl
			""")
	int updateVerificationByCodeAndOriginalUrl(
			@Param("code") String code,
			@Param("originalUrl") String originalUrl,
			@Param("status") LinkStatus status,
			@Param("verifiedAt") Instant verifiedAt
	);

}

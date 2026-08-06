package link.srrrg.link;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LinkRepository extends JpaRepository<Link, Long> {

	@EntityGraph(attributePaths = "project")
	Optional<Link> findByCode(String code);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "project")
	@Query("select l from Link l where l.code = :code")
	Optional<Link> lockByCode(@Param("code") String code);
	Optional<Link> findByIdempotencyApiKeyIdAndIdempotencyKey(Long apiKeyId, String idempotencyKey);
	List<Link> findByProjectIdAndDeletedFalseOrderByIdDesc(Long projectId);
	List<Link> findByProjectIdAndDeletedFalseOrderByIdDesc(Long projectId, Pageable pageable);
	List<Link> findByProjectIdAndDeletedFalseAndIdLessThanOrderByIdDesc(Long projectId, Long id, Pageable pageable);

	@Modifying
	@Query("update Link l set l.accessCount = l.accessCount + 1 where l.code = :code")
	int incrementAccessCountByCode(@Param("code") String code);

	@Modifying
	@Query("""
			update Link l
			   set l.accessCount = l.accessCount + 1,
			       l.redirectCount = l.redirectCount + 1
			 where l.code = :code
			""")
	int incrementAccessAndRedirectCountsByCode(@Param("code") String code);

}

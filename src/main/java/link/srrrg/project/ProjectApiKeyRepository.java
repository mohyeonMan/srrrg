package link.srrrg.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * API 키 조회. 인증 경로가 이 저장소 하나를 거치므로, 여기 조회 조건이 곧 어떤 키가 유효한지의 기준이다.
 */
public interface ProjectApiKeyRepository extends JpaRepository<ProjectApiKey, Long> {
	List<ProjectApiKey> findByProjectIdOrderByCreatedAtDesc(Long projectId);

	// 삭제된 프로젝트의 키로는 인증되지 않아야 한다. fetch join이 소프트 삭제 제약을 적용해 행을 떨어뜨린다.
	@Query("select k from ProjectApiKey k join fetch k.project where k.keyHash = :keyHash")
	Optional<ProjectApiKey> findActiveByKeyHash(@Param("keyHash") String keyHash);
}

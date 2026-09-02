package link.srrrg.project;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 초대 조회. 토큰으로 찾는 경로만 프로젝트를 조인해, 삭제된 프로젝트의 초대는 수락되지 않게 한다.
 */
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {
	// 삭제된 프로젝트의 초대는 조회되지 않아야 한다.
	@Query("select i from ProjectInvitation i join fetch i.project where i.tokenHash = :tokenHash")
	Optional<ProjectInvitation> findActiveByTokenHash(@Param("tokenHash") String tokenHash);

	List<ProjectInvitation> findByProjectId(Long projectId);
	List<ProjectInvitation> findByProjectIdAndCancelledAtIsNullAndAcceptedAtIsNull(Long projectId);
	Optional<ProjectInvitation> findByProjectIdAndEmailAndCancelledAtIsNullAndAcceptedAtIsNull(Long projectId, String email);
}

package link.srrrg.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.ProjectMemberId> {
	List<ProjectMember> findByIdUserId(Long userId);
	List<ProjectMember> findByIdUserIdAndProjectArchivedAtIsNull(Long userId);
	List<ProjectMember> findByIdProjectId(Long projectId);
	Optional<ProjectMember> findByIdProjectIdAndIdUserId(Long projectId, Long userId);
	long countByIdProjectIdAndRole(Long projectId, ProjectRole role);
	long countByIdUserIdAndRole(Long userId, ProjectRole role);
	long countByIdUserIdAndRoleAndProjectArchivedAtIsNull(Long userId, ProjectRole role);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from ProjectMember m where m.id.projectId = :projectId and m.id.userId = :userId")
	Optional<ProjectMember> lockByProjectAndUser(@Param("projectId") Long projectId, @Param("userId") Long userId);
}

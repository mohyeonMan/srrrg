package link.srrrg.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/**
 * Project에는 {@code @SoftDelete}가 걸려 있다. 삭제된 프로젝트의 멤버십을 배제해야 하는 조회는
 * {@code join fetch m.project}를 명시한다 — 소프트 삭제 제약이 조인에 적용되어 행이 통째로 빠진다.
 * 사후에 프로젝트 상태를 확인하는 방식은 검사를 빠뜨릴 수 있어 쓰지 않는다.
 */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.ProjectMemberId> {
	List<ProjectMember> findByIdUserId(Long userId);

	@Query("select m from ProjectMember m join fetch m.project where m.id.userId = :userId")
	List<ProjectMember> findActiveByUserId(@Param("userId") Long userId);

	List<ProjectMember> findByIdProjectId(Long projectId);

	@Query("select m from ProjectMember m join fetch m.project where m.id.projectId = :projectId and m.id.userId = :userId")
	Optional<ProjectMember> findActiveByProjectAndUser(@Param("projectId") Long projectId, @Param("userId") Long userId);

	long countByIdProjectIdAndRole(Long projectId, ProjectRole role);
	long countByIdUserIdAndRole(Long userId, ProjectRole role);

	@Query("""
			select count(m) from ProjectMember m
			where m.id.userId = :userId and m.role = :role
			  and exists (select 1 from Project p where p.id = m.id.projectId)
			""")
	// 소유 프로젝트 수 상한 검사용. exists 절이 삭제된 프로젝트를 세지 않게 해,
	// 프로젝트를 지운 뒤에도 상한에 걸려 새로 만들지 못하는 상황을 막는다.
	long countActiveByUserIdAndRole(@Param("userId") Long userId, @Param("role") ProjectRole role);

	// requireRole이 이미 프로젝트 상태를 통과시킨 뒤에만 호출되므로 여기서는 프로젝트를 조인하지 않는다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from ProjectMember m where m.id.projectId = :projectId and m.id.userId = :userId")
	Optional<ProjectMember> lockByProjectAndUser(@Param("projectId") Long projectId, @Param("userId") Long userId);
}

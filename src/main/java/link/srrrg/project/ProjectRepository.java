package link.srrrg.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
	boolean existsBySubdomain(String subdomain);

	/**
	 * 엔티티 삭제(delete(entity))를 쓰면 같은 트랜잭션에 로드된 ProjectMember가 제거된 Project를
	 * 참조한 채 flush되어 TransientPropertyValueException이 난다. bulk delete는 엔티티 상태를
	 * 거치지 않고 @SoftDelete UPDATE만 실행한다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Project p where p.id = :id")
	int softDeleteById(@Param("id") Long id);
}

package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * UTM 템플릿 조회. 조회 메서드에 projectId를 함께 받는 것이 프로젝트 격리의 근거다.
 * 삭제 표시는 {@code deletedAt}이라 조회 조건에 직접 넣어야 하며, 자동으로 걸러지지 않는다.
 */
public interface UtmTemplateRepository extends JpaRepository<UtmTemplate, Long> {
	Optional<UtmTemplate> findByIdAndProjectId(Long id, Long projectId);
	List<UtmTemplate> findByProjectIdAndDeletedAtIsNullOrderByIdDesc(Long projectId);
	boolean existsByProjectIdAndNameAndDeletedAtIsNull(Long projectId, String name);

	/**
	 * 필드 추가 시 템플릿을 행 잠금으로 읽는다. 활성 필드 수 상한을 세고 저장하는 두 단계라,
	 * 잠금이 없으면 동시에 들어온 요청이 모두 상한 미만을 보고 통과해 상한을 넘긴다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from UtmTemplate t where t.id = :id and t.project.id = :projectId")
	Optional<UtmTemplate> lockByIdAndProjectId(@Param("id") Long id, @Param("projectId") Long projectId);
}

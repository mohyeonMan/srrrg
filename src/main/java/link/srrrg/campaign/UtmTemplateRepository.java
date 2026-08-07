package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtmTemplateRepository extends JpaRepository<UtmTemplate, Long> {
	Optional<UtmTemplate> findByIdAndProjectId(Long id, Long projectId);
	List<UtmTemplate> findByProjectIdAndDeletedAtIsNullOrderByIdDesc(Long projectId);
	boolean existsByProjectIdAndNameAndDeletedAtIsNull(Long projectId, String name);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from UtmTemplate t where t.id = :id and t.project.id = :projectId")
	Optional<UtmTemplate> lockByIdAndProjectId(@Param("id") Long id, @Param("projectId") Long projectId);
}

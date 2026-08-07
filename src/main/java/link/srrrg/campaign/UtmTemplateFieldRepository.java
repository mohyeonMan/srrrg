package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UtmTemplateFieldRepository extends JpaRepository<UtmTemplateField, Long> {
	Optional<UtmTemplateField> findByIdAndUtmTemplateId(Long id, Long utmTemplateId);
	List<UtmTemplateField> findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(Long utmTemplateId);
	List<UtmTemplateField> findByUtmTemplateIdOrderByNameAsc(Long utmTemplateId);
	Optional<UtmTemplateField> findByUtmTemplateIdAndNameAndDeletedAtIsNull(Long utmTemplateId, String name);
	long countByUtmTemplateIdAndDeletedAtIsNull(Long utmTemplateId);
}

package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 템플릿 필드 조회. {@code DeletedAtIsNull}이 붙은 메서드만 지금 유효한 필드를 돌려준다.
 * 삭제된 필드까지 포함하는 메서드는 실패 CSV처럼 과거 값을 함께 보여줘야 하는 곳에서만 쓴다.
 */
public interface UtmTemplateFieldRepository extends JpaRepository<UtmTemplateField, Long> {
	Optional<UtmTemplateField> findByIdAndUtmTemplateId(Long id, Long utmTemplateId);
	List<UtmTemplateField> findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(Long utmTemplateId);
	List<UtmTemplateField> findByUtmTemplateIdOrderByNameAsc(Long utmTemplateId);
	Optional<UtmTemplateField> findByUtmTemplateIdAndNameAndDeletedAtIsNull(Long utmTemplateId, String name);
	long countByUtmTemplateIdAndDeletedAtIsNull(Long utmTemplateId);
}

package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 캠페인 조회. 대부분의 호출자가 곧바로 UTM 템플릿을 필요로 해서 함께 로딩한다.
 *
 * <p>{@code findByIdAndProjectId}가 API key 경로의 인가 지점이다. 프로젝트 조건이 빠지면
 * 키만 유효하면 다른 프로젝트의 캠페인에 닿는다.</p>
 */
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
	@Override
	@EntityGraph(attributePaths = "utmTemplate")
	Optional<Campaign> findById(Long id);

	@EntityGraph(attributePaths = "utmTemplate")
	Optional<Campaign> findByIdAndProjectId(Long id, Long projectId);
	@EntityGraph(attributePaths = "utmTemplate")
	List<Campaign> findByProjectIdOrderByIdDesc(Long projectId, Pageable pageable);
	@EntityGraph(attributePaths = "utmTemplate")
	List<Campaign> findByProjectIdAndIdLessThanOrderByIdDesc(Long projectId, Long id, Pageable pageable);
	long countByUtmTemplateId(Long utmTemplateId);
}

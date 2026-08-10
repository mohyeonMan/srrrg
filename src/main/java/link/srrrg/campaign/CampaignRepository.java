package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

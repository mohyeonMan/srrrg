package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
	Optional<Campaign> findByIdAndProjectId(Long id, Long projectId);
	List<Campaign> findByProjectIdAndArchivedAtIsNullOrderByIdDesc(Long projectId, Pageable pageable);
	List<Campaign> findByProjectIdAndArchivedAtIsNullAndIdLessThanOrderByIdDesc(Long projectId, Long id, Pageable pageable);
	long countByUtmTemplateIdAndArchivedAtIsNull(Long utmTemplateId);
}

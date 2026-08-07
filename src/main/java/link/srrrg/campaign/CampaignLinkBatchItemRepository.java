package link.srrrg.campaign;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignLinkBatchItemRepository extends JpaRepository<CampaignLinkBatchItem, CampaignLinkBatchItem.CampaignLinkBatchItemId> {
	List<CampaignLinkBatchItem> findByBatchIdOrderByIdItemIndexAsc(Long batchId);
}

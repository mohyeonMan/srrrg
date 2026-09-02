package link.srrrg.campaign;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * batch에 포함된 링크 조회. 재시도 응답이 원래 요청 순서를 유지하도록 항목 순번으로 정렬해 읽는다.
 */
public interface CampaignLinkBatchItemRepository extends JpaRepository<CampaignLinkBatchItem, CampaignLinkBatchItem.CampaignLinkBatchItemId> {
	List<CampaignLinkBatchItem> findByBatchIdOrderByIdItemIndexAsc(Long batchId);
}

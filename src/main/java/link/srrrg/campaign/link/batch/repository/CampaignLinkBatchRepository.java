package link.srrrg.campaign.link.batch.repository;

import link.srrrg.campaign.link.batch.model.CampaignLinkBatch;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * batch 멱등 기록 조회. API key와 멱등 키의 조합으로 찾으므로, 다른 키가 같은 문자열을 써도 간섭하지 않는다.
 */
public interface CampaignLinkBatchRepository extends JpaRepository<CampaignLinkBatch, Long> {
	Optional<CampaignLinkBatch> findByApiKeyIdAndIdempotencyKey(Long apiKeyId, String idempotencyKey);
}

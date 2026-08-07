package link.srrrg.campaign;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignLinkBatchRepository extends JpaRepository<CampaignLinkBatch, Long> {
	Optional<CampaignLinkBatch> findByApiKeyIdAndIdempotencyKey(Long apiKeyId, String idempotencyKey);
}

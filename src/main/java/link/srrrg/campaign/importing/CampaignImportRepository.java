package link.srrrg.campaign.importing;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignImportRepository extends JpaRepository<CampaignImport, Long> {
	Optional<CampaignImport> findByIdAndCampaignId(Long id, Long campaignId);
	Optional<CampaignImport> findByCampaignIdAndIdempotencyKey(Long campaignId, String idempotencyKey);

	@Modifying
	@Query("""
			update CampaignImport i
			   set i.status = link.srrrg.campaign.importing.ImportStatus.CANCELLED,
			       i.completedAt = CURRENT_TIMESTAMP,
			       i.leaseOwner = null,
			       i.leaseExpiresAt = null
			 where i.campaign.id = :campaignId
			   and i.status in (link.srrrg.campaign.importing.ImportStatus.PENDING, link.srrrg.campaign.importing.ImportStatus.PROCESSING)
			""")
	int cancelActiveByCampaignId(@Param("campaignId") Long campaignId);

	@Query(value = """
			select * from campaign_imports
			 where status = 'PENDING'
			    or (status = 'PROCESSING' and lease_expires_at < :now)
			 order by id asc
			 limit 1
			 for update skip locked
			""", nativeQuery = true)
	Optional<CampaignImport> findNextClaimable(@Param("now") Instant now);
}

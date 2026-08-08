package link.srrrg.campaign.importing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignImportRowUtmValueRepository extends JpaRepository<CampaignImportRowUtmValue, CampaignImportRowUtmValue.CampaignImportRowUtmValueId> {
	List<CampaignImportRowUtmValue> findByImportRowId(Long importRowId);
}

package link.srrrg.campaign.importing;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignImportRowRepository extends JpaRepository<CampaignImportRow, Long> {
	List<CampaignImportRow> findByCampaignImportIdAndStatusOrderByRowNumberAsc(Long importId, ImportRowStatus status, Pageable pageable);
	List<CampaignImportRow> findByCampaignImportIdAndStatusOrderByRowNumberAsc(Long importId, ImportRowStatus status);
	List<CampaignImportRow> findByCampaignImportIdOrderByRowNumberAsc(Long importId);
	boolean existsByCampaignImportIdAndExternalIdAndIdNot(Long importId, String externalId, Long excludingRowId);
}

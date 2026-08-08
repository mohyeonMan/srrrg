package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignUtmDefaultRepository extends JpaRepository<CampaignUtmDefault, CampaignUtmDefault.CampaignUtmDefaultId> {

	@Query("select d from CampaignUtmDefault d where d.campaign.id = :campaignId order by d.id.fieldName asc")
	List<CampaignUtmDefault> findByCampaignIdOrderByFieldNameAsc(@Param("campaignId") Long campaignId);

	@Query("select d from CampaignUtmDefault d where d.campaign.id = :campaignId and d.id.fieldName = :fieldName")
	Optional<CampaignUtmDefault> findByCampaignIdAndFieldName(@Param("campaignId") Long campaignId,
			@Param("fieldName") String fieldName);
}

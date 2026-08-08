package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignUtmDefaultRepository extends JpaRepository<CampaignUtmDefault, CampaignUtmDefault.CampaignUtmDefaultId> {

	@Query("select d from CampaignUtmDefault d where d.campaign.id = :campaignId order by d.field.name asc")
	@EntityGraph(attributePaths = "field")
	List<CampaignUtmDefault> findByCampaignIdOrderByFieldNameAsc(@Param("campaignId") Long campaignId);

	Optional<CampaignUtmDefault> findByCampaignIdAndFieldId(Long campaignId, Long fieldId);

	@Modifying
	@Query("delete from CampaignUtmDefault d where d.campaign.id = :campaignId")
	void deleteByCampaignId(@Param("campaignId") Long campaignId);
}

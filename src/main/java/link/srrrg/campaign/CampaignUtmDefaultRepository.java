package link.srrrg.campaign;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 캠페인 UTM 기본값 조회. 복합 키의 필드 이름으로 찾아야 해서 파생 쿼리 대신 JPQL을 직접 쓴다.
 * 여기서 읽는 값은 삭제된 필드의 것도 포함하므로, 화면에 보여줄 때는 활성 필드로 걸러야 한다.
 */
public interface CampaignUtmDefaultRepository extends JpaRepository<CampaignUtmDefault, CampaignUtmDefault.CampaignUtmDefaultId> {

	@Query("select d from CampaignUtmDefault d where d.campaign.id = :campaignId order by d.id.fieldName asc")
	List<CampaignUtmDefault> findByCampaignIdOrderByFieldNameAsc(@Param("campaignId") Long campaignId);

	@Query("select d from CampaignUtmDefault d where d.campaign.id = :campaignId and d.id.fieldName = :fieldName")
	Optional<CampaignUtmDefault> findByCampaignIdAndFieldName(@Param("campaignId") Long campaignId,
			@Param("fieldName") String fieldName);
}

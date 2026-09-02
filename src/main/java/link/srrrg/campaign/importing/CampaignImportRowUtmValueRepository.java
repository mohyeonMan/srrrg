package link.srrrg.campaign.importing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 임포트 행에 지정된 UTM 값 조회. 링크가 만들어지기 전 단계의 값이다.
 */
public interface CampaignImportRowUtmValueRepository extends JpaRepository<CampaignImportRowUtmValue, CampaignImportRowUtmValue.CampaignImportRowUtmValueId> {
	List<CampaignImportRowUtmValue> findByImportRowId(Long importRowId);
}

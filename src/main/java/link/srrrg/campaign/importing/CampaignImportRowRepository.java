package link.srrrg.campaign.importing;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 임포트 행 조회. worker가 PENDING 행을 페이지 단위로 집어 가고, 실패 CSV는 FAILED 행만 읽는다.
 * 행 번호 순서를 유지해야 사용자가 원본 파일과 대조할 수 있다.
 */
public interface CampaignImportRowRepository extends JpaRepository<CampaignImportRow, Long> {
	List<CampaignImportRow> findByCampaignImportIdAndStatusOrderByRowNumberAsc(Long importId, ImportRowStatus status, Pageable pageable);
	List<CampaignImportRow> findByCampaignImportIdAndStatusOrderByRowNumberAsc(Long importId, ImportRowStatus status);
	List<CampaignImportRow> findByCampaignImportIdOrderByRowNumberAsc(Long importId);
	boolean existsByCampaignImportIdAndExternalIdAndIdNot(Long importId, String externalId, Long excludingRowId);
}

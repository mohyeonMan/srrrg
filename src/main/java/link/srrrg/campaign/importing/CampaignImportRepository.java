package link.srrrg.campaign.importing;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * CSV 임포트 작업 행을 다룬다. 아래 두 쿼리가 분산 처리의 핵심이다.
 */
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
	/**
	 * 캠페인 삭제 시 진행 중인 임포트를 한 번에 취소한다. lease까지 비우는 것이 중요하다.
	 * 남겨 두면 다른 파드의 worker가 만료를 기다렸다가 이미 삭제된 캠페인의 작업을 다시 집는다.
	 */
	int cancelActiveByCampaignId(@Param("campaignId") Long campaignId);

	@Query(value = """
			select * from campaign_imports
			 where status = 'PENDING'
			    or (status = 'PROCESSING' and lease_expires_at < :now)
			 order by id asc
			 limit 1
			 for update skip locked
			""", nativeQuery = true)
	/**
	 * 처리할 작업 하나를 잠금과 함께 집어 온다. 여러 파드가 동시에 호출하는 것을 전제로 한 쿼리다.
	 *
	 * <p>{@code FOR UPDATE SKIP LOCKED}가 핵심이다. 다른 트랜잭션이 이미 잠근 행은 기다리지 않고
	 * 건너뛰므로, 파드들이 서로 다른 작업을 집는다. 이것이 없으면 모든 파드가 같은 첫 행에서 대기한다.</p>
	 *
	 * <p>PENDING뿐 아니라 lease가 만료된 PROCESSING도 대상에 넣는다. 처리하던 파드가 죽었을 때
	 * 작업을 회수하는 경로가 이것 하나뿐이다.</p>
	 *
	 * <p>JPQL이 아니라 네이티브 SQL인 것은 {@code SKIP LOCKED}를 표현하기 위해서다.</p>
	 */
	Optional<CampaignImport> findNextClaimable(@Param("now") Instant now);
}

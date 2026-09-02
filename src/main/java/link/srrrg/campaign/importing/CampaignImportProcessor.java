package link.srrrg.campaign.importing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.campaign.Campaign;
import link.srrrg.campaign.UtmTemplate;
import link.srrrg.link.ExternalIdConflictException;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.management.LinkManagementService;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link CampaignImportWorker}가 호출하는 실제 claim/처리 로직.
 * 이 클래스의 메서드는 서로를 호출하지 않는다 — 전부 {@link CampaignImportWorker}가 외부에서 하나씩 호출해야
 * 각 {@code @Transactional}이 self-invocation으로 프록시를 건너뛰지 않고 실제로 커밋된다.
 */
@Component
@Slf4j
public class CampaignImportProcessor {

	static final int ROW_PAGE_SIZE = 100;

	// lease는 처리 중이라는 표시이자 그 유효기간이다. 파드가 죽으면 상태를 정리할 사람이 없으므로,
	// 시간이 지나면 자동으로 회수되게 해서 job이 PROCESSING 상태로 영원히 묶이는 것을 막는다.
	// 시도 횟수 상한은 계속 죽는 job이 무한히 재시도되는 것을 끊는다.
	private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
	private static final int MAX_ATTEMPTS = 3;

	private final CampaignImportRepository imports;
	private final CampaignImportRowRepository importRows;
	private final CampaignImportRowUtmValueRepository importRowUtmValues;
	private final LinkManagementService linkManagement;

	public CampaignImportProcessor(CampaignImportRepository imports, CampaignImportRowRepository importRows,
			CampaignImportRowUtmValueRepository importRowUtmValues, LinkManagementService linkManagement) {
		this.imports = imports;
		this.importRows = importRows;
		this.importRowUtmValues = importRowUtmValues;
		this.linkManagement = linkManagement;
	}

	/**
	 * 처리할 import 하나를 선점한다. 여러 파드가 동시에 호출해도 같은 job을 둘이 가져가지 않는다.
	 *
	 * <p>보장은 두 겹이다. 조회 쿼리의 {@code FOR UPDATE SKIP LOCKED}가 다른 트랜잭션이 잠근 행을
	 * 건너뛰게 하고, 그 위에서 {@code tryAcquireLease}가 상태와 lease 만료를 다시 확인한다.
	 * 앞의 것은 동시성을, 뒤의 것은 이미 처리 중이거나 끝난 job을 거른다.</p>
	 *
	 * @return 선점한 import의 id, 또는 처리할 것이 없으면 {@code null}
	 */
	@Transactional
	public Long claimNext(String workerId) {
		Instant now = Instant.now();
		return imports.findNextClaimable(now)
				.filter(campaignImport -> campaignImport.tryAcquireLease(workerId, now, now.plus(LEASE_DURATION), MAX_ATTEMPTS))
				.map(CampaignImport::getId)
				.orElse(null);
	}

	/**
	 * 남은 행의 id만 페이지 단위로 읽는다. 엔티티가 아니라 id만 넘기는 것은 행마다 트랜잭션이 달라
	 * 여기서 읽은 엔티티를 다음 트랜잭션에서 그대로 쓸 수 없기 때문이다.
	 */
	@Transactional(readOnly = true)
	public List<Long> fetchPendingRowIds(Long importId) {
		return importRows.findByCampaignImportIdAndStatusOrderByRowNumberAsc(importId, ImportRowStatus.PENDING, PageRequest.of(0, ROW_PAGE_SIZE))
				.stream().map(CampaignImportRow::getId).toList();
	}

	/**
	 * CSV 한 행을 링크로 만든다. 행 하나가 독립된 트랜잭션이라 실패해도 다른 행에 영향을 주지 않는다.
	 *
	 * <p>모든 실패를 예외로 올리지 않고 행에 오류 코드로 기록하는 것이 이 메서드의 계약이다.
	 * 예외를 던지면 그 행의 실패 기록까지 롤백되어 무엇이 왜 실패했는지 남지 않고, worker 루프도 멈춘다.
	 * 사용자는 나중에 실패 행만 CSV로 내려받아 고쳐 다시 올린다.</p>
	 *
	 * <p>이미 PENDING이 아닌 행은 조용히 지나간다. 다른 파드가 먼저 처리했거나 재시도로 다시 들어온 경우다.</p>
	 *
	 * <p>import가 PROCESSING이 아니면 행을 실패로 남긴다. 처리 도중 캠페인이나 import가 취소된 경우이며,
	 * 여기서 링크를 계속 만들면 취소했는데도 링크가 늘어난다.</p>
	 */
	@Transactional
	public void processRow(Long importId, Long rowId) {
		CampaignImport campaignImport = imports.findById(importId).orElse(null);
		CampaignImportRow row = importRows.findById(rowId).orElse(null);
		if (campaignImport == null || row == null || row.getStatus() != ImportRowStatus.PENDING) return;

		if (campaignImport.getStatus() != ImportStatus.PROCESSING) {
			row.fail("IMPORT_CANCELLED", "캠페인 또는 import가 취소되어 처리되지 않았습니다.");
			campaignImport.recordRowResult(false);
			return;
		}

		try {
			Campaign campaign = campaignImport.getCampaign();
			UtmTemplate template = campaignImport.getUtmTemplate();
			Map<String, String> resolved = importRowUtmValues.findByImportRowId(rowId).stream()
					.collect(java.util.stream.Collectors.toMap(CampaignImportRowUtmValue::getFieldName, CampaignImportRowUtmValue::getValue));
			var createdLink = linkManagement.createForCampaign(row.getOriginalUrl(), null, campaign.getProject(),
					null, null, null, null,
					campaign, template, row.getExternalId(), resolved);
			row.succeed(createdLink.getId());
			campaignImport.recordRowResult(true);
		} catch (ExternalIdConflictException exception) {
			row.fail("EXTERNAL_ID_CONFLICT", exception.getMessage());
			campaignImport.recordRowResult(false);
		} catch (UnsafeUrlException exception) {
			row.fail("URL_THREAT_DETECTED", exception.getMessage());
			campaignImport.recordRowResult(false);
		} catch (UrlRiskCheckFailedException exception) {
			row.fail("URL_CHECK_FAILED", exception.getMessage());
			campaignImport.recordRowResult(false);
		} catch (IllegalArgumentException exception) {
			row.fail("INVALID_ROW", exception.getMessage());
			campaignImport.recordRowResult(false);
			// 예상하지 못한 오류까지 행 실패로 흡수한다. 여기서 예외가 올라가면 worker의 drain 루프가 끊겨
			// 남은 행이 통째로 멈추므로, 원인은 로그로 남기고 처리는 계속한다.
		} catch (RuntimeException exception) {
			log.error("Unexpected CSV row processing error: importId={}, rowId={}", importId, rowId, exception);
			row.fail("UNEXPECTED_ERROR", "처리 중 오류가 발생했습니다.");
			campaignImport.recordRowResult(false);
		}
	}

	/**
	 * 남은 행이 없을 때만 import를 완료로 표시한다. 남아 있으면 아무것도 하지 않아,
	 * lease가 만료된 뒤 다른 파드가 이어받을 수 있게 PROCESSING 상태를 유지한다.
	 * 이미 취소되거나 끝난 import는 건드리지 않는다.
	 */
	@Transactional
	public void finalizeImport(Long importId) {
		CampaignImport campaignImport = imports.findById(importId).orElse(null);
		if (campaignImport == null || campaignImport.getStatus() != ImportStatus.PROCESSING) return;
		boolean stillPending = !importRows.findByCampaignImportIdAndStatusOrderByRowNumberAsc(importId, ImportRowStatus.PENDING,
				PageRequest.of(0, 1)).isEmpty();
		if (stillPending) return;
		campaignImport.complete(Instant.now());
	}
}

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
import link.srrrg.campaign.UtmTemplateField;
import link.srrrg.domain.ProjectDomainService;
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

	private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
	private static final int MAX_ATTEMPTS = 3;

	private final CampaignImportRepository imports;
	private final CampaignImportRowRepository importRows;
	private final CampaignImportRowUtmValueRepository importRowUtmValues;
	private final LinkManagementService linkManagement;
	private final ProjectDomainService domains;

	public CampaignImportProcessor(CampaignImportRepository imports, CampaignImportRowRepository importRows,
			CampaignImportRowUtmValueRepository importRowUtmValues, LinkManagementService linkManagement,
			ProjectDomainService domains) {
		this.imports = imports;
		this.importRows = importRows;
		this.importRowUtmValues = importRowUtmValues;
		this.linkManagement = linkManagement;
		this.domains = domains;
	}

	@Transactional
	public Long claimNext(String workerId) {
		Instant now = Instant.now();
		return imports.findNextClaimable(now)
				.filter(campaignImport -> campaignImport.tryAcquireLease(workerId, now, now.plus(LEASE_DURATION), MAX_ATTEMPTS))
				.map(CampaignImport::getId)
				.orElse(null);
	}

	@Transactional(readOnly = true)
	public List<Long> fetchPendingRowIds(Long importId) {
		return importRows.findByCampaignImportIdAndStatusOrderByRowNumberAsc(importId, ImportRowStatus.PENDING, PageRequest.of(0, ROW_PAGE_SIZE))
				.stream().map(CampaignImportRow::getId).toList();
	}

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
			Map<UtmTemplateField, String> resolved = importRowUtmValues.findByImportRowId(rowId).stream()
					.collect(java.util.stream.Collectors.toMap(CampaignImportRowUtmValue::getField, CampaignImportRowUtmValue::getValue));
			var createdLink = linkManagement.createForCampaign(row.getOriginalUrl(), null, campaign.getProject(),
					domains.get(campaign.getProject().getId()), null, null, null, null,
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
		} catch (RuntimeException exception) {
			log.error("Unexpected CSV row processing error: importId={}, rowId={}", importId, rowId, exception);
			row.fail("UNEXPECTED_ERROR", "처리 중 오류가 발생했습니다.");
			campaignImport.recordRowResult(false);
		}
	}

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

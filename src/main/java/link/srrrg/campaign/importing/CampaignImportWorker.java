package link.srrrg.campaign.importing;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL lease 기반으로 CSV import job 하나를 claim해 처리하는 단일 worker.
 * 여러 pod에서 동시에 실행돼도 {@code FOR UPDATE SKIP LOCKED}와 lease로 같은 job을 중복 처리하지 않는다.
 * 이 클래스가 {@link CampaignImportProcessor}의 각 {@code @Transactional} 메서드를 하나씩 호출해야
 * self-invocation 없이 실제로 트랜잭션 프록시를 거친다. 이 클래스 스스로 {@code @Transactional}을 갖지 않는다.
 */
@Component
@Slf4j
public class CampaignImportWorker {

	private static final int MAX_ROWS_PER_IMPORT = 10_000;

	private final String workerId = UUID.randomUUID().toString();
	private final CampaignImportProcessor processor;

	public CampaignImportWorker(CampaignImportProcessor processor) {
		this.processor = processor;
	}

	@Scheduled(fixedDelayString = "${srrrg.csv-worker.fixed-delay-ms:2000}", initialDelayString = "${srrrg.csv-worker.initial-delay-ms:5000}")
	public void tick() {
		try {
			Long importId = processor.claimNext(workerId);
			if (importId == null) return;
			drain(importId);
		} catch (RuntimeException exception) {
			log.error("CSV import worker tick failed", exception);
		}
	}

	private void drain(Long importId) {
		int processed = 0;
		while (processed < MAX_ROWS_PER_IMPORT) {
			List<Long> pendingRowIds = processor.fetchPendingRowIds(importId);
			if (pendingRowIds.isEmpty()) break;
			for (Long rowId : pendingRowIds) {
				processor.processRow(importId, rowId);
				processed++;
			}
		}
		if (processed >= MAX_ROWS_PER_IMPORT) {
			log.error("CSV import worker stopped after processing the maximum row cap without draining all pending rows: importId={}", importId);
		}
		processor.finalizeImport(importId);
	}
}

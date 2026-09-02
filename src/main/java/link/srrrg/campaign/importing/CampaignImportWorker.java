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

	// 한 tick에서 처리할 행 수의 상한. 무한 루프 방지용 안전장치다. 행 처리에 버그가 있어
	// PENDING이 줄지 않으면 이 상한이 없을 때 worker 스레드가 영원히 돌며 lease만 만료된다.
	private static final int MAX_ROWS_PER_IMPORT = 10_000;

	// 파드마다 다른 식별자. lease 소유자를 기록해 어느 파드가 잡고 있는지 남긴다.
	// 재기동하면 값이 바뀌므로 죽은 파드가 남긴 lease는 만료 시각으로만 회수된다.
	private final String workerId = UUID.randomUUID().toString();
	private final CampaignImportProcessor processor;

	public CampaignImportWorker(CampaignImportProcessor processor) {
		this.processor = processor;
	}

	/**
	 * 주기적으로 깨어나 처리할 import가 있으면 하나만 집어 끝까지 처리한다.
	 *
	 * <p>모든 파드에서 동시에 실행되는 것을 전제로 한다. 중복 처리를 막는 것은 스케줄러가 아니라
	 * {@code claimNext}의 DB 잠금이며, 이 메서드는 그 결과를 받아 진행할 뿐이다.</p>
	 *
	 * <p>예외를 밖으로 던지지 않고 로그만 남긴다. 스케줄된 메서드에서 예외가 새어 나가면
	 * 다음 실행에 영향을 줄 수 있고, 한 job의 실패로 worker 전체가 멈추면 안 되기 때문이다.</p>
	 */
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

	/**
	 * 한 import의 남은 행을 페이지 단위로 반복 처리한다.
	 *
	 * <p>행마다 별도 트랜잭션으로 처리하는 것이 핵심이다. 한 트랜잭션으로 묶으면 한 행의 실패가
	 * 전체를 롤백시켜 진행 상황이 사라지고, 만 건짜리 트랜잭션이 오래 열려 있게 된다.
	 * 대신 처리 도중 파드가 죽으면 그때까지 성공한 행은 남고, lease가 만료된 뒤 다른 파드가
	 * 남은 PENDING 행부터 이어받는다.</p>
	 *
	 * <p>마지막에 완료 처리를 부르지만 남은 행이 있으면 그쪽에서 아무것도 하지 않는다.</p>
	 */
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

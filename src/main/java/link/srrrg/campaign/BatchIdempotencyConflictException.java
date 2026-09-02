package link.srrrg.campaign;

/**
 * 같은 Idempotency-Key로 내용이 다른 batch를 보냈거나, 같은 키의 다른 요청이 먼저 커밋된 경우.
 * 후자는 재시도하면 기존 결과를 돌려받는다.
 */
public class BatchIdempotencyConflictException extends RuntimeException {
	public BatchIdempotencyConflictException() {
		super("같은 Idempotency-Key를 다른 batch 요청에 사용할 수 없습니다. 이미 처리된 요청이면 다시 시도해 기존 결과를 조회하세요.");
	}
}

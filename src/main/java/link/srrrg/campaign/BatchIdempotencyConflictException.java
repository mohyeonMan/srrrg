package link.srrrg.campaign;

public class BatchIdempotencyConflictException extends RuntimeException {
	public BatchIdempotencyConflictException() {
		super("같은 Idempotency-Key를 다른 batch 요청에 사용할 수 없습니다. 이미 처리된 요청이면 다시 시도해 기존 결과를 조회하세요.");
	}
}

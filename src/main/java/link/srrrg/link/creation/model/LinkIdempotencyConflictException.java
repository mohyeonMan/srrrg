package link.srrrg.link.creation.model;

/** 같은 멱등 키를 서로 다른 링크 생성 요청에 재사용했을 때 발생한다. */
public class LinkIdempotencyConflictException extends RuntimeException {
	public LinkIdempotencyConflictException() {
		super("같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다.");
	}
}

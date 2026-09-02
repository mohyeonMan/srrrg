package link.srrrg.common.ratelimit;

/**
 * 한도를 넘은 요청을 중단시키는 예외. 서비스 계층에서 던지면 각 표면의 예외 처리기가 429로 변환한다.
 * {@code GlobalExceptionHandler}는 이 예외의 {@code retryAfterSeconds}를 {@code Retry-After} 헤더로 내보내므로,
 * 응답에서 대기 시간을 빼거나 형식을 바꾸려면 여기가 아니라 예외 처리기를 고쳐야 한다.
 */
public class RateLimitExceededException extends RuntimeException {
	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super("요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}

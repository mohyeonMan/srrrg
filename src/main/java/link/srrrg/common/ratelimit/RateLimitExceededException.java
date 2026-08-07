package link.srrrg.common.ratelimit;

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

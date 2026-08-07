package link.srrrg.common.ratelimit;

public record RateLimitOutcome(boolean allowed, long retryAfterSeconds) {
	public static RateLimitOutcome failOpen() {
		return new RateLimitOutcome(true, 0);
	}
}

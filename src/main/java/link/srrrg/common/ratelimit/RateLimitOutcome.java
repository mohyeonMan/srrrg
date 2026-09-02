package link.srrrg.common.ratelimit;

/**
 * 레이트리밋 판정 한 건의 결과. Redis 장애로 판정 자체를 못 한 경우도 {@link #failOpen()}으로 이 타입에 담긴다.
 *
 * @param allowed 요청을 계속 진행해도 되는지 여부
 * @param retryAfterSeconds 거부된 경우 클라이언트에게 알려줄 대기 시간. 허용된 경우 의미 없음
 */
public record RateLimitOutcome(boolean allowed, long retryAfterSeconds) {
	/**
	 * Redis에 접근하지 못했을 때 쓰는 결과. 카운터를 확인할 수 없다는 이유로 서비스를 멈추지 않고 통과시킨다.
	 * 즉 Redis가 죽어 있는 동안에는 한도가 사실상 없는 상태가 되므로, 남용 차단을 이 판정에만 의존하면 안 된다.
	 */
	public static RateLimitOutcome failOpen() {
		return new RateLimitOutcome(true, 0);
	}
}

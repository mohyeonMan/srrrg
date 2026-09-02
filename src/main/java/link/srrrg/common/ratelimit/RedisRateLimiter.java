package link.srrrg.common.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 기반 고정 윈도우 rate limit·quota. Redis 연결 실패 시 fail-open으로 요청을 허용한다.
 * 공유 Redis 인스턴스 하나에 여러 애플리케이션이 접속하므로 key에는 항상 환경별 prefix를 사용해야 한다.
 */
@Component
@Slf4j
public class RedisRateLimiter {

	// 카운터 증가와 만료 설정을 Lua로 묶은 이유는 원자성 때문이다. INCR과 EXPIRE를 애플리케이션에서
	// 두 번 호출하면 그 사이에 파드가 죽거나 다른 요청이 끼어들 수 있고, EXPIRE를 놓친 키는
	// 만료되지 않은 채 남아 그 사용자를 영구히 차단하게 된다. Redis는 스크립트를 단일 스레드로 실행하므로
	// 파드가 몇 개든 이 블록 전체가 한 번에 처리된다.
	// 고정 윈도우 방식이라 윈도우 경계 직전과 직후에 걸쳐 한도의 두 배까지 통과할 수 있다.
	// 균등한 제한이 필요하면 sliding window가 필요하며, 여기서는 그 정확도 대신 단순함을 택했다.
	private static final String FIXED_WINDOW_LUA = """
			local current = redis.call('INCR', KEYS[1])
			if current == 1 then
			  redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			local ttl = redis.call('TTL', KEYS[1])
			return {current, ttl}
			""";

	// 대량 발행 한도용. 요청 하나가 여러 개를 소비하므로 한도 확인과 증가를 한 스크립트로 묶어
	// 두 요청이 동시에 남은 할당량을 확인하고 둘 다 통과하는 상황을 막는다.
	// 한도를 넘으면 아무것도 증가시키지 않는 전부-아니면-전무 방식이다. 부분 허용을 하면
	// 호출자가 링크 일부만 만들어진 상태를 처리해야 하는데, 그 복잡도를 감수할 만한 이득이 없다.
	private static final String QUOTA_LUA = """
			local current = tonumber(redis.call('GET', KEYS[1]) or '0')
			local increment = tonumber(ARGV[1])
			local limit = tonumber(ARGV[2])
			if current + increment > limit then
			  local ttl = redis.call('TTL', KEYS[1])
			  if ttl < 0 then ttl = tonumber(ARGV[3]) end
			  return {0, current, ttl}
			end
			local newVal = redis.call('INCRBY', KEYS[1], increment)
			if newVal == increment then
			  redis.call('EXPIRE', KEYS[1], ARGV[3])
			end
			local ttl2 = redis.call('TTL', KEYS[1])
			return {1, newVal, ttl2}
			""";

	private final StringRedisTemplate redis;
	private final RedisScript<List> fixedWindowScript;
	private final RedisScript<List> quotaScript;

	@SuppressWarnings({"unchecked", "rawtypes"})
	public RedisRateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
		this.fixedWindowScript = RedisScript.of(FIXED_WINDOW_LUA, List.class);
		this.quotaScript = RedisScript.of(QUOTA_LUA, List.class);
	}

	/**
	 * 고정 윈도우 카운터를 1 증가시키고 한도 안인지 판정한다.
	 *
	 * @param key 이미 환경 prefix가 붙은 완전한 Redis 키
	 * @param limit 윈도우당 허용 횟수
	 * @param window 카운터가 초기화되는 주기
	 * @return 허용 여부와 재시도까지 남은 초. Redis 오류나 예상 밖 반환 형태에서는 fail-open 결과
	 */
	@SuppressWarnings("unchecked")
	public RateLimitOutcome consume(String key, long limit, Duration window) {
		try {
			List<Long> result = redis.execute(fixedWindowScript, List.of(key), String.valueOf(window.toSeconds()));
			// 스크립트가 예상과 다른 형태를 돌려준 상황은 판정 불가로 보고 통과시킨다.
			if (result == null || result.size() < 2) return RateLimitOutcome.failOpen();
			long current = result.get(0);
			long ttl = result.get(1);
			// TTL이 0이나 -1로 오면 클라이언트가 대기 없이 즉시 재시도하므로 최소 1초는 보장한다.
			return new RateLimitOutcome(current <= limit, Math.max(ttl, 1));
		} catch (RuntimeException exception) {
			// Redis 장애로 판정을 못 했다고 서비스 전체를 멈추지는 않는다(fail-open).
			// 이 로그가 이어지는 동안에는 이 키의 한도가 걸리지 않는 상태다.
			log.warn("Redis rate limit check failed, failing open: key={}", key, exception);
			return RateLimitOutcome.failOpen();
		}
	}

	/**
	 * 요청 하나가 여러 건을 소비하는 할당량을 확인하고 통과할 때만 그만큼 증가시킨다.
	 *
	 * @param key 이미 환경 prefix가 붙은 완전한 Redis 키
	 * @param increment 이번 요청이 소비하려는 수량
	 * @param limit 윈도우당 총 허용 수량
	 * @param window 할당량이 초기화되는 주기
	 * @return 허용 여부와 재시도까지 남은 초. 거부된 경우 카운터는 증가하지 않는다
	 */
	@SuppressWarnings("unchecked")
	public RateLimitOutcome consumeQuota(String key, long increment, long limit, Duration window) {
		try {
			List<Long> result = redis.execute(quotaScript, List.of(key),
					String.valueOf(increment), String.valueOf(limit), String.valueOf(window.toSeconds()));
			if (result == null || result.size() < 3) return RateLimitOutcome.failOpen();
			boolean allowed = result.get(0) == 1;
			long ttl = result.get(2);
			return new RateLimitOutcome(allowed, Math.max(ttl, 1));
		} catch (RuntimeException exception) {
			log.warn("Redis quota check failed, failing open: key={}", key, exception);
			return RateLimitOutcome.failOpen();
		}
	}
}

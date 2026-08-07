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

	private static final String FIXED_WINDOW_LUA = """
			local current = redis.call('INCR', KEYS[1])
			if current == 1 then
			  redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			local ttl = redis.call('TTL', KEYS[1])
			return {current, ttl}
			""";

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

	@SuppressWarnings("unchecked")
	public RateLimitOutcome consume(String key, long limit, Duration window) {
		try {
			List<Long> result = redis.execute(fixedWindowScript, List.of(key), String.valueOf(window.toSeconds()));
			if (result == null || result.size() < 2) return RateLimitOutcome.failOpen();
			long current = result.get(0);
			long ttl = result.get(1);
			return new RateLimitOutcome(current <= limit, Math.max(ttl, 1));
		} catch (RuntimeException exception) {
			log.warn("Redis rate limit check failed, failing open: key={}", key, exception);
			return RateLimitOutcome.failOpen();
		}
	}

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

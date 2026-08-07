package link.srrrg.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 이미 확정된 운영 한도를 이름 있는 메서드로 노출한다. 실제 원자적 카운팅은 {@link RedisRateLimiter}가 담당한다.
 */
@Service
public class RateLimitService {

	private static final long BULK_LINKS_PER_PROJECT_PER_DAY = 50_000;

	private final RedisRateLimiter limiter;
	private final String prefix;

	public RateLimitService(RedisRateLimiter limiter, @Value("${srrrg.rate-limit.key-prefix:srrrg:dev:}") String prefix) {
		this.limiter = limiter;
		this.prefix = prefix;
	}

	public void checkAnonymousLinkCreation(String ipAddress) {
		String id = ipAddress == null ? "unknown" : hash(ipAddress);
		require(prefix + "rl:anon:min:" + id, 10, Duration.ofMinutes(1));
		require(prefix + "rl:anon:day:" + id, 200, Duration.ofDays(1));
	}

	public void checkApiKeyRead(Long apiKeyId) {
		require(prefix + "rl:key:read:" + apiKeyId, 300, Duration.ofMinutes(1));
	}

	public void checkApiKeyWrite(Long apiKeyId) {
		require(prefix + "rl:key:write:" + apiKeyId, 60, Duration.ofMinutes(1));
	}

	public void checkJsonBatch(Long projectId) {
		require(prefix + "rl:batch:" + projectId, 2, Duration.ofMinutes(1));
	}

	public void checkCsvUpload(Long projectId) {
		require(prefix + "rl:csv:" + projectId, 5, Duration.ofHours(1));
	}

	public void checkBulkLinkQuota(Long projectId, int linkCount) {
		RateLimitOutcome outcome = limiter.consumeQuota(prefix + "quota:bulk:" + projectId, linkCount,
				BULK_LINKS_PER_PROJECT_PER_DAY, Duration.ofDays(1));
		if (!outcome.allowed()) {
			throw new RateLimitExceededException(outcome.retryAfterSeconds());
		}
	}

	private void require(String key, long limit, Duration window) {
		RateLimitOutcome outcome = limiter.consume(key, limit, window);
		if (!outcome.allowed()) {
			throw new RateLimitExceededException(outcome.retryAfterSeconds());
		}
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

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

	// 한도 수치를 호출부에 흩지 않고 여기 모아 두는 이유는, 값이 바뀔 때 고쳐야 할 곳을 한 군데로 줄이고
	// 어떤 행위에 어떤 한도가 걸려 있는지 한눈에 검토할 수 있게 하기 위해서다.
	private static final long BULK_LINKS_PER_PROJECT_PER_DAY = 50_000;

	private final RedisRateLimiter limiter;
	private final String prefix;

		// prefix는 환경 구분자다. 개발·운영이 같은 Redis를 공유할 때 이것이 없으면
		// 개발 트래픽이 운영 사용자의 카운터를 소진시킨다. 기본값은 개발용이며 운영에서는 반드시 주입한다.
	public RateLimitService(RedisRateLimiter limiter, @Value("${srrrg.rate-limit.key-prefix:srrrg:dev:}") String prefix) {
		this.limiter = limiter;
		this.prefix = prefix;
	}

	/**
	 * 비회원 링크 생성 한도를 확인한다. 인증이 없어 식별자가 IP뿐이므로 분당·일당 두 창을 함께 건다.
	 * 짧은 창은 순간적인 자동 생성을, 긴 창은 느리게 지속되는 남용을 막는다.
	 *
	 * @param ipAddress {@code ClientRequestInfoResolver}가 해석한 클라이언트 IP.
	 *                  {@code srrrg.trust-forwarded-headers}가 켜진 환경에서만 프록시 헤더가 반영되고,
	 *                  꺼져 있으면 접속 소켓의 주소다. {@code null}이면 모든 미상 요청이 하나의 카운터를
	 *                  공유하므로 그만큼 더 빨리 막힌다
	 * @throws RateLimitExceededException 두 창 중 하나라도 한도를 넘은 경우
	 */
	public void checkAnonymousLinkCreation(String ipAddress) {
		String id = ipAddress == null ? "unknown" : hash(ipAddress);
		require(prefix + "rl:anon:min:" + id, 10, Duration.ofMinutes(1));
		require(prefix + "rl:anon:day:" + id, 200, Duration.ofDays(1));
	}

	// 읽기와 쓰기에 서로 다른 키와 한도를 쓴다. 조회가 아무리 잦아도 쓰기 한도를 갉아먹지 않게 하고,
	// 되돌리기 어려운 쓰기 쪽을 더 낮게 잡기 위해서다.
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

	/**
	 * 대량 발행이 프로젝트 일일 총량을 넘지 않는지 확인한다. 호출 횟수가 아니라 만들어질 링크 수를 소비한다.
	 * 요청 하나가 만드는 링크 수가 한 건부터 배치 상한까지 크게 달라, 횟수만 세면 실제 생성량이 통제되지 않는다.
	 *
	 * @param linkCount 이번 요청이 만들려는 링크 수
	 * @throws RateLimitExceededException 남은 할당량이 모자란 경우. 이때 할당량은 전혀 소비되지 않는다
	 */
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

	/**
	 * IP처럼 그 자체가 개인정보인 값을 Redis 키에 그대로 쓰지 않기 위해 해시로 바꾼다.
	 * 키를 덤프해도 원문 IP가 드러나지 않게 하려는 것이며, 같은 IP는 항상 같은 키로 매핑되어야 하므로
	 * 솔트를 쓰지 않는다. 즉 IP 후보를 아는 사람이 대조하는 것은 막지 못한다.
	 */
	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

package link.srrrg.link.management.dto;

import java.time.Instant;

/**
 * 링크 생성 응답. {@code secretKey} 원문이 나가는 유일한 지점이며 DB에는 해시만 남으므로,
 * 이 응답을 놓치면 그 링크는 다시 관리할 수 없다. 로그나 다른 응답에 이 값을 실어서는 안 된다.
 */
public record CreateLinkResponse(
		String code,
		String shortUrl,
		String secretKey,
		Instant expiresAt
) {
}

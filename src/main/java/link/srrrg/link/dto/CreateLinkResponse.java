package link.srrrg.link.dto;

import java.time.Instant;

public record CreateLinkResponse(
		String code,
		String shortUrl,
		String secretKey,
		Instant expiresAt
) {
}

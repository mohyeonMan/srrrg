package link.srrrg.link.management.dto;

import java.time.Instant;

public record LinkManagementResponse(
		String code,
		String shortUrl,
		String originalUrl,
		Long campaignId,
		boolean editable,
		Instant expiresAt,
		Instant createdAt,
		Instant updatedAt
) {
}

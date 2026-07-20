package link.srrrg.link.management.dto;

import java.time.Instant;

public record LinkManagementResponse(
		String code,
		String shortUrl,
		String originalUrl,
		Instant expiresAt,
		LinkStatisticsSummary statistics,
		Instant createdAt,
		Instant updatedAt
) {
}

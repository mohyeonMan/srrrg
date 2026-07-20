package link.srrrg.link.management.dto;

import java.time.Instant;

import link.srrrg.link.LinkStatus;

public record LinkManagementResponse(
		String code,
		String shortUrl,
		String originalUrl,
		Instant expiresAt,
		LinkStatus status,
		Instant verifiedAt,
		LinkStatisticsSummary statistics,
		Instant createdAt,
		Instant updatedAt
) {
}

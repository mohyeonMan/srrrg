package link.srrrg.link.management.dto;

import java.time.Instant;

/**
 * 링크 관리 화면과 API가 공유하는 응답. secret key는 포함하지 않는다.
 * {@code editable}은 호출자의 권한 판정 결과이고, 캠페인 링크는 {@code originalUrl}이 비어 있을 수 있다.
 * 캠페인 기본 목적지를 상속한다는 뜻이다.
 */
public record LinkManagementResponse(
		String code,
		String name,
		String shortUrl,
		String originalUrl,
		Long campaignId,
		boolean editable,
		Instant expiresAt,
		Instant createdAt,
		Instant updatedAt
) {
}

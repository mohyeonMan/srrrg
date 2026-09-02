package link.srrrg.campaign.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.Size;

/**
 * 캠페인 링크 생성 요청. 웹, API key, JSON batch, CSV 임포트가 모두 이 형태로 모인다.
 *
 * <p>목적지를 비워 보낼 수 있다. 캠페인 기본 목적지를 상속하겠다는 뜻이며, 그래서 여기서
 * 필수로 강제하지 않는다. {@code utmValues}에 담긴 필드만 링크에 저장되고, 빠진 필드는
 * 리다이렉트 시점의 캠페인 기본값을 따라간다.</p>
 */
public record CreateCampaignLinkRequest(
		@Size(max = 2048, message = "원본 URL은 2,048자 이하여야 합니다.")
		String originalUrl,
		Instant expiresAt,
		@Size(max = 100, message = "external_id는 100자 이하여야 합니다.")
		String externalId,
		Map<String, String> utmValues,
		@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
		String name
) {
	public CreateCampaignLinkRequest(String originalUrl, Instant expiresAt, String externalId, Map<String, String> utmValues) {
		this(originalUrl, expiresAt, externalId, utmValues, null);
	}

	public Map<String, String> utmValuesOrEmpty() {
		return utmValues == null ? Map.of() : utmValues;
	}

	public String normalizedExternalId() {
		return externalId == null || externalId.isBlank() ? null : externalId.trim();
	}

	public String normalizedOriginalUrl() {
		return originalUrl == null || originalUrl.isBlank() ? null : originalUrl.trim();
	}

	public String normalizedName() {
		return name == null || name.isBlank() ? null : name.trim();
	}
}

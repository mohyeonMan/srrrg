package link.srrrg.campaign.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.Size;

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

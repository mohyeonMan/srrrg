package link.srrrg.campaign.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCampaignLinkRequest(
		@NotBlank(message = "원본 URL은 필수입니다.")
		@Size(max = 2048, message = "원본 URL은 2,048자 이하여야 합니다.")
		String originalUrl,
		Instant expiresAt,
		@Size(max = 100, message = "external_id는 100자 이하여야 합니다.")
		String externalId,
		Map<String, String> utmValues
) {
	public Map<String, String> utmValuesOrEmpty() {
		return utmValues == null ? Map.of() : utmValues;
	}

	public String normalizedExternalId() {
		return externalId == null || externalId.isBlank() ? null : externalId.trim();
	}
}

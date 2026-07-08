package link.srrrg.link.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
		@NotBlank(message = "원본 URL은 필수입니다.")
		@Size(max = 2048, message = "원본 URL은 2,048자 이하여야 합니다.")
		String originalUrl,
		Instant expiresAt
) {
}

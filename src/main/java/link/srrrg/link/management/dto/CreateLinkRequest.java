package link.srrrg.link.management.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 링크 생성 요청. 익명 생성과 프로젝트 생성이 같은 타입을 쓰며, 어느 쪽인지는 호출 경로가 정한다.
 * 여기 검증은 형식만 보고, 스킴이나 내부망 여부는 {@code UrlValidator}가 따로 확인한다.
 */
public record CreateLinkRequest(
		@NotBlank(message = "원본 URL은 필수입니다.")
		@Size(max = 2048, message = "원본 URL은 2,048자 이하여야 합니다.")
		String originalUrl,
		Instant expiresAt,
		@Size(max = 100, message = "이름은 100자 이하여야 합니다.")
		String name
) {
	public CreateLinkRequest(String originalUrl, Instant expiresAt) {
		this(originalUrl, expiresAt, null);
	}

	/**
	 * 공백만 있는 이름은 이름 없음과 같게 다룬다. 그대로 저장하면 목록에서 빈 칸으로 보이는 링크가 생긴다.
	 */
	public String normalizedName() {
		return name == null || name.isBlank() ? null : name.trim();
	}
}

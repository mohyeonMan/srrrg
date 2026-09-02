package link.srrrg.link.management.dto;

import java.time.Instant;

import link.srrrg.link.Link;

/**
 * 웹 프로젝트 링크 목록과 생성 응답에서 공유하는 표현이다.
 * JPA 엔티티를 HTTP 응답에 직접 노출하지 않고 화면에 필요한 필드만 고정한다.
 */
public record ProjectLinkResponse(String code, String subdomain, String name, String originalUrl, Instant expiresAt,
		Instant createdAt) {
	public static ProjectLinkResponse from(Link link) {
		return new ProjectLinkResponse(link.getCode(), link.getSubdomain(), link.getName(), link.getOriginalUrl(),
				link.getExpiresAt(), link.getCreatedAt());
	}
}

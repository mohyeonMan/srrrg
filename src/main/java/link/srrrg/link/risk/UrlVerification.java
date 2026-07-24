package link.srrrg.link.risk;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DB에 저장된 SAFE 또는 THREAT 검증 결과.
 * expiresAt은 Google이 내려준 캐시 기간을 verifiedAt에 더한 시각이다.
 */
@Entity
@Table(name = "url_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrlVerification {

	@Id
	@Column(name = "url_hash", nullable = false, length = 64)
	private String urlHash;

	@Column(name = "original_url", nullable = false, length = 2048)
	private String originalUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RiskVerdict verdict;

	@Column(name = "verified_at", nullable = false)
	private Instant verifiedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	boolean matches(String url) {
		return originalUrl.equals(url);
	}

	boolean isFreshAt(Instant instant) {
		return expiresAt.isAfter(instant);
	}

	UrlRiskAssessment toAssessment() {
		return new UrlRiskAssessment(verdict, verifiedAt, expiresAt);
	}
}

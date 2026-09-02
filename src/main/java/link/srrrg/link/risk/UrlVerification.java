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
/**
 * DB에 저장된 SAFE 또는 THREAT 검증 결과.
 * expiresAt은 Google이 내려준 캐시 기간을 verifiedAt에 더한 시각이다.
 *
 * <p>{@code originalUrl}을 함께 두는 것은 해시 충돌 확인용이다. 기본 키가 URL 해시라
 * 원문 대조 없이는 다른 URL의 판정을 물려받을 수 있다.</p>
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

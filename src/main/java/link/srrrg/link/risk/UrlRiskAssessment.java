package link.srrrg.link.risk;

import java.time.Instant;

/**
 * URL 위험도 검사 결과. UNKNOWN은 캐시하지 않으므로 expiresAt이 없다.
 */
public record UrlRiskAssessment(
		RiskVerdict verdict,
		Instant verifiedAt,
		Instant expiresAt
) {
	public static UrlRiskAssessment unknown(Instant verifiedAt) {
		return new UrlRiskAssessment(RiskVerdict.UNKNOWN, verifiedAt, null);
	}

	public boolean isCacheable() {
		return verdict != RiskVerdict.UNKNOWN && expiresAt != null;
	}
}

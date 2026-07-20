package link.srrrg.link.risk;

import java.time.Instant;

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

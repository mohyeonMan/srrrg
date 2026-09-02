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

	/**
	 * UNKNOWN은 판정이 아니라 검사 실패이므로 저장하지 않는다. 저장하면 외부 검사가 복구된 뒤에도
	 * 만료 전까지 실패 상태를 계속 돌려주게 된다.
	 */
	public boolean isCacheable() {
		return verdict != RiskVerdict.UNKNOWN && expiresAt != null;
	}
}

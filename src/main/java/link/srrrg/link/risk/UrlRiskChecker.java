package link.srrrg.link.risk;

/**
 * URL 위험도 판정 공급자. 설정에 따라 실제 외부 검사(Google Safe Browsing) 또는
 * 항상 안전으로 답하는 개발용 구현 중 하나만 빈으로 올라간다.
 *
 * <p>구현은 예외를 던지지 않고 판정 불가를 {@link RiskVerdict#UNKNOWN}으로 돌려줘야 한다.
 * 그 값을 안전으로 볼지 거부로 볼지는 호출자가 정하며, 현재 정책은 거부다.</p>
 */
public interface UrlRiskChecker {

	UrlRiskAssessment check(String url);
}

package link.srrrg.link.access;

/**
 * User-Agent 해석 결과. 판별하지 못한 항목은 {@code null}이나 {@code Other}로 남으므로,
 * 이 값으로 집계할 때는 미분류가 섞여 있다는 전제로 읽어야 한다.
 */
public record UserAgentInfo(
		String browserName,
		String browserVersion,
		String osName,
		String osVersion,
		String deviceType,
		boolean bot
) {
}

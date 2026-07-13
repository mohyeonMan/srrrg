package link.srrrg.link.risk;

public enum UrlRiskCheckResult {
	// 알려진 위협이 응답에 포함되지 않은 상태임.
	NO_THREAT_FOUND,
	// Google 응답에 하나 이상의 위협이 포함된 상태임.
	THREAT_DETECTED,
	// 설정, 네트워크, HTTP 또는 응답 처리 문제로 검사하지 못한 상태임.
	CHECK_FAILED
}

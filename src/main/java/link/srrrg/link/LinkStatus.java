package link.srrrg.link;

import link.srrrg.link.risk.UrlRiskCheckResult;

public enum LinkStatus {
	// 기존 데이터이거나 아직 검사 이력이 없는 상태임.
	NOT_VERIFIED,
	NO_THREAT_FOUND,
	THREAT_DETECTED,
	CHECK_FAILED;

	public static LinkStatus from(UrlRiskCheckResult result) {
		return LinkStatus.valueOf(result.name());
	}
}

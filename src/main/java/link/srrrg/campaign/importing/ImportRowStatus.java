package link.srrrg.campaign.importing;

/**
 * 임포트 행 하나의 상태. FAILED 행은 지우지 않고 남겨, 사용자가 실패 목록만 CSV로 내려받아
 * 고친 뒤 다시 올릴 수 있게 한다.
 */
public enum ImportRowStatus {
	PENDING, SUCCEEDED, FAILED
}

package link.srrrg.campaign.importing;

/**
 * 임포트 작업의 생애 주기. PROCESSING은 lease를 가진 파드가 처리 중이라는 뜻이지만,
 * lease가 만료된 PROCESSING은 처리하던 파드가 사라졌다는 뜻이라 다시 선점 대상이 된다.
 * FAILED는 시도 횟수를 다 쓴 경우이고, CANCELLED는 캠페인이나 작업이 취소된 경우다.
 */
public enum ImportStatus {
	PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
}

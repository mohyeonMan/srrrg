package link.srrrg.campaign.importing;

/**
 * 같은 프로젝트에 이미 진행 중인 임포트가 있는 경우. 동시에 여러 임포트가 돌면
 * 대량 발행 할당량과 처리 순서를 통제하기 어려워 하나로 제한한다. 앞의 작업이 끝나면 재시도할 수 있다.
 */
public class ActiveImportConflictException extends RuntimeException {
	public ActiveImportConflictException() {
		super("이 프로젝트에는 이미 진행 중인 CSV import가 있습니다. 완료 후 다시 시도하세요.");
	}
}

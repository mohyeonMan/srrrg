package link.srrrg.campaign.importing;

public class ActiveImportConflictException extends RuntimeException {
	public ActiveImportConflictException() {
		super("이 프로젝트에는 이미 진행 중인 CSV import가 있습니다. 완료 후 다시 시도하세요.");
	}
}

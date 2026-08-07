package link.srrrg.link;

public class ExternalIdConflictException extends RuntimeException {
	public ExternalIdConflictException() {
		super("같은 external_id가 이미 이 캠페인에 사용 중입니다.");
	}
}

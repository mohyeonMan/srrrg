package link.srrrg.link;

/**
 * 같은 캠페인 안에서 외부 id가 중복된 경우. 외부 시스템의 식별자를 그대로 받아 두는 값이라
 * 중복을 허용하면 그 시스템에서 링크를 되찾을 수 없다. 재시도해도 같은 결과인 영구 충돌이다.
 */
public class ExternalIdConflictException extends RuntimeException {
	public ExternalIdConflictException() {
		super("같은 external_id가 이미 이 캠페인에 사용 중입니다.");
	}
}

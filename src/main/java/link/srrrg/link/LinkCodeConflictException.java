package link.srrrg.link;

/**
 * 사용자가 지정한 단축 코드가 이미 쓰이고 있는 경우. 자동 생성 코드의 충돌은 재시도로 처리되므로
 * 이 예외가 되지 않는다.
 */
public class LinkCodeConflictException extends RuntimeException {
	public LinkCodeConflictException() {
		super("대상 프로젝트 도메인에 같은 단축 코드가 이미 존재합니다.");
	}
}

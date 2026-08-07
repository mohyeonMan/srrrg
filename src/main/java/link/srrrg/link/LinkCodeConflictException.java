package link.srrrg.link;

public class LinkCodeConflictException extends RuntimeException {
	public LinkCodeConflictException() {
		super("대상 프로젝트 도메인에 같은 단축 코드가 이미 존재합니다.");
	}
}

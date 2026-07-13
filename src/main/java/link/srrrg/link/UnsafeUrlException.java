package link.srrrg.link;

public class UnsafeUrlException extends RuntimeException {
	public UnsafeUrlException() {
		super("알려진 위협이 탐지된 URL은 등록할 수 없습니다.");
	}
}

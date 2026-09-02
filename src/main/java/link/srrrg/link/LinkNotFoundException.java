package link.srrrg.link;

/**
 * 링크를 쓸 수 없는 상황 중 존재 자체를 감춰야 하는 경우를 모두 404로 합치는 예외.
 * 없는 코드, 삭제된 링크, 삭제된 프로젝트의 링크, secret key 불일치가 여기로 온다.
 * 구분해서 알려주면 코드를 훑어 실재하는 링크를 가려낼 수 있다.
 */
public class LinkNotFoundException extends RuntimeException {

	public LinkNotFoundException() {
		super("링크를 찾을 수 없습니다.");
	}
}

package link.srrrg.link;

public class LinkGoneException extends RuntimeException {

	public LinkGoneException() {
		super("삭제되었거나 만료된 링크입니다.");
	}
}

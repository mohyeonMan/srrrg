package link.srrrg.link;

public class LinkGoneException extends RuntimeException {
	private final Reason reason;

	public LinkGoneException() {
		this(Reason.UNKNOWN);
	}

	public LinkGoneException(Reason reason) {
		super(messageFor(reason));
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

	private static String messageFor(Reason reason) {
		return switch (reason) {
			case EXPIRED -> "이 단축 링크는 설정된 만료 시각이 지나 더 이상 이동할 수 없습니다.";
			case DELETED -> "이 단축 링크는 생성자가 삭제해 더 이상 이동할 수 없습니다.";
			case NO_DESTINATION -> "이 단축 링크에 사용할 목적지 URL이 없습니다.";
			case UNKNOWN -> "삭제되었거나 만료된 링크입니다.";
		};
	}

	public enum Reason {
		EXPIRED,
		DELETED,
		NO_DESTINATION,
		UNKNOWN
	}
}

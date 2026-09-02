package link.srrrg.link;

/**
 * 링크는 존재하지만 더 이상 이동할 수 없는 상태. 404와 달리 존재 자체는 인정하므로,
 * 이 예외를 쓰는 경우는 존재를 감출 필요가 없는 상황으로 한정한다.
 */
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
			case NO_DESTINATION -> "이 단축 링크에 사용할 목적지 URL이 없습니다.";
			case UNKNOWN -> "사용할 수 없는 링크입니다.";
		};
	}

	/**
	 * 삭제는 여기 없다. 삭제된 링크는 {@code @SoftDelete}로 조회되지 않아 404가 된다.
	 * 410은 "존재하지만 더 이상 이동할 수 없는" 상태에만 쓴다.
	 */
	public enum Reason {
		EXPIRED,
		NO_DESTINATION,
		UNKNOWN
	}
}

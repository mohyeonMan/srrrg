package link.srrrg.common;

/**
 * {@code /api/v1/**} 표면에서 상태 코드와 안정적인 오류 코드를 함께 전달하는 예외다.
 * 웹 API의 {@link ApiErrorResponse}와 섞이지 않도록 공개 API 전용 처리기만 이 타입을 변환한다.
 */
public class PublicApiException extends RuntimeException {
	private final int status;
	private final String code;

	public PublicApiException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public int status() {
		return status;
	}

	public String code() {
		return code;
	}
}

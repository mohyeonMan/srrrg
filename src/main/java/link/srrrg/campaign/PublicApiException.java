package link.srrrg.campaign;

/**
 * {@code /api/v1/**} 표면 전용 오류 신호. 상태 코드와 오류 코드를 함께 담아
 * {@code PublicCampaignApiExceptionHandler}가 RFC 7807 형식으로 변환한다.
 * 웹 표면의 예외와 타입을 분리해 두어야 두 표면의 응답 형식이 섞이지 않는다.
 */
public class PublicApiException extends RuntimeException {
	final int status;
	final String code;

	public PublicApiException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}
}

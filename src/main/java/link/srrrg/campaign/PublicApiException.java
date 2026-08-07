package link.srrrg.campaign;

public class PublicApiException extends RuntimeException {
	final int status;
	final String code;

	public PublicApiException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}
}

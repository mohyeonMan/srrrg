package link.srrrg.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";
	private static final String LINK_NOT_FOUND = "LINK_NOT_FOUND";
	private static final String LINK_GONE = "LINK_GONE";
	private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getAllErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("요청 값이 올바르지 않습니다.");

		return badRequest(message);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return badRequest(exception.getMessage());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException exception) {
		return badRequest("관리용 secret key 헤더가 필요합니다.");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		return badRequest("요청 본문 형식이 올바르지 않습니다.");
	}

	@ExceptionHandler(LinkNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleLinkNotFound(LinkNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse(LINK_NOT_FOUND, exception.getMessage()));
	}

	@ExceptionHandler(LinkGoneException.class)
	public ResponseEntity<ApiErrorResponse> handleLinkGone(LinkGoneException exception) {
		return ResponseEntity.status(HttpStatus.GONE)
				.body(new ApiErrorResponse(LINK_GONE, exception.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiErrorResponse(INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
	}

	private ResponseEntity<ApiErrorResponse> badRequest(String message) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse(INVALID_REQUEST, message));
	}
}

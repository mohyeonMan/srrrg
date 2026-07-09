package link.srrrg.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";
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

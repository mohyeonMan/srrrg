package link.srrrg.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";

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

	@ExceptionHandler(LinkNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleLinkNotFound(LinkNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse("LINK_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(LinkGoneException.class)
	public ResponseEntity<ApiErrorResponse> handleLinkGone(LinkGoneException exception) {
		return ResponseEntity.status(HttpStatus.GONE)
				.body(new ApiErrorResponse("LINK_GONE", exception.getMessage()));
	}

	private ResponseEntity<ApiErrorResponse> badRequest(String message) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse(INVALID_REQUEST, message));
	}
}

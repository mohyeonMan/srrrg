package link.srrrg.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkCodeConflictException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";
	private static final String LINK_NOT_FOUND = "LINK_NOT_FOUND";
	private static final String LINK_GONE = "LINK_GONE";
	private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
	private static final String URL_THREAT_DETECTED = "URL_THREAT_DETECTED";
	private static final String URL_CHECK_FAILED = "URL_CHECK_FAILED";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getAllErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("요청 값이 올바르지 않습니다.");

		log.debug("Request validation failed: errorCount={}", exception.getBindingResult().getErrorCount());
		return badRequest(message);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return badRequest(exception.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	public ResponseEntity<ApiErrorResponse> handleSecurity(SecurityException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ApiErrorResponse("PROJECT_ACCESS_DENIED", "프로젝트 접근 권한이 없습니다."));
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
		log.debug("API request failed: code={}", LINK_NOT_FOUND);
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse(LINK_NOT_FOUND, exception.getMessage()));
	}

	@ExceptionHandler(LinkGoneException.class)
	public ResponseEntity<ApiErrorResponse> handleLinkGone(LinkGoneException exception) {
		log.debug("API request failed: code={}", LINK_GONE);
		return ResponseEntity.status(HttpStatus.GONE)
				.body(new ApiErrorResponse(LINK_GONE, exception.getMessage()));
	}

	@ExceptionHandler(LinkCodeConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleLinkCodeConflict(LinkCodeConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("LINK_CODE_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(UnsafeUrlException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsafeUrl(UnsafeUrlException exception) {
		// 알려진 위협 URL은 클라이언트가 수정할 수 있는 요청 오류로 응답함.
		log.warn("API request rejected: code={}", URL_THREAT_DETECTED);
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(URL_THREAT_DETECTED, exception.getMessage()));
	}

	@ExceptionHandler(UrlRiskCheckFailedException.class)
	public ResponseEntity<ApiErrorResponse> handleUrlRiskCheckFailed(UrlRiskCheckFailedException exception) {
		// 외부 검사 불가 상태는 재시도 가능한 서비스 오류로 응답함.
		log.warn("API request unavailable: code={}", URL_CHECK_FAILED);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ApiErrorResponse(URL_CHECK_FAILED, exception.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
		log.error("Unexpected API error: type={}", exception.getClass().getSimpleName(), exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiErrorResponse(INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
	}

	private ResponseEntity<ApiErrorResponse> badRequest(String message) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse(INVALID_REQUEST, message));
	}
}

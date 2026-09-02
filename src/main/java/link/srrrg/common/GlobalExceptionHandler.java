package link.srrrg.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import link.srrrg.campaign.BatchIdempotencyConflictException;
import link.srrrg.campaign.CampaignNotFoundException;
import link.srrrg.campaign.importing.ActiveImportConflictException;
import link.srrrg.campaign.importing.CampaignImportIdempotencyConflictException;
import link.srrrg.common.ratelimit.RateLimitExceededException;
import link.srrrg.link.ExternalIdConflictException;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkCodeConflictException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import lombok.extern.slf4j.Slf4j;

/**
 * 컨트롤러에서 빠져나온 예외를 {@link ApiErrorResponse} 형식의 HTTP 응답으로 바꾸는 최종 방어선이다.
 *
 * <p>범위를 제한하지 않은 {@code @RestControllerAdvice}라 모든 컨트롤러에 걸리지만, 세 API 표면 중
 * 실제로 이 형식을 쓰는 것은 웹 API({@code /api/web/**})와 비인증 링크 API({@code /api/links/**})다.
 * {@code /api/v1/**}의 캠페인·UTM 템플릿 컨트롤러는 {@code PublicCampaignApiExceptionHandler}가,
 * 사용자에게 HTML 오류 화면을 보여야 하는 {@code /{code}} 리다이렉트는 {@code RedirectExceptionHandler}가
 * {@code assignableTypes}로 대상 컨트롤러를 지정해 따로 처리한다.
 * 이 중 우선순위를 명시한 것은 {@code @Order(HIGHEST_PRECEDENCE)}를 붙인 {@code RedirectExceptionHandler}뿐이고,
 * 나머지 둘은 순위 선언이 없어 기본 순위가 같다. 따라서 같은 예외 타입을 여러 곳에 등록해 두면
 * 어느 응답 형식이 나갈지는 advice 정렬 결과에 달린다. 새 예외를 만들면 표면별 처리기에 각각 등록한다.</p>
 *
 * <p>응답 메시지는 사용자에게 그대로 보이므로 예외의 원문 메시지를 넘길 때는 그 메시지에 내부 구조가
 * 드러나지 않는지 확인한다. 예상하지 못한 예외는 메시지를 감추고 스택트레이스만 로그로 남긴다.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";
	private static final String LINK_NOT_FOUND = "LINK_NOT_FOUND";
	private static final String LINK_GONE = "LINK_GONE";
	private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
	private static final String URL_THREAT_DETECTED = "URL_THREAT_DETECTED";
	private static final String URL_CHECK_FAILED = "URL_CHECK_FAILED";

	/**
	 * Bean Validation 실패를 400으로 바꾼다. 여러 필드가 함께 실패해도 첫 메시지 하나만 응답에 담으므로,
	 * 클라이언트는 남은 오류를 한 번에 알 수 없고 고칠 때마다 다시 요청하게 된다.
	 * 실패한 필드 수는 응답이 아니라 로그로만 남긴다.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getAllErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("요청 값이 올바르지 않습니다.");

		log.debug("Request validation failed: errorCount={}", exception.getBindingResult().getErrorCount());
		return badRequest(message);
	}

	/**
	 * 서비스 계층이 업무 규칙 위반에 쓰는 {@code IllegalArgumentException}을 400으로 바꾼다.
	 * 여기서 메시지를 그대로 노출하므로, 서비스에서 이 예외를 던질 때는 사용자에게 보여도 되는 한국어 문장만 넣는다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return badRequest(exception.getMessage());
	}

	@ExceptionHandler(CampaignNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleCampaignNotFound(CampaignNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse("CAMPAIGN_NOT_FOUND", exception.getMessage()));
	}

	/**
	 * 권한 확인 실패를 403으로 바꾼다. 예외 메시지를 그대로 쓰지 않고 고정 문구로 덮으므로,
	 * 어떤 역할이 모자랐는지는 응답에 드러나지 않는다. 원인은 던진 쪽에서 로그로 남겨야 한다.
	 */
	@ExceptionHandler(SecurityException.class)
	public ResponseEntity<ApiErrorResponse> handleSecurity(SecurityException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ApiErrorResponse("PROJECT_ACCESS_DENIED", "프로젝트 접근 권한이 없습니다."));
	}

	/**
	 * 필수 헤더 누락을 400으로 바꾼다. 어떤 헤더가 빠졌는지와 무관하게 secret key 안내 문구가 나간다.
	 * 현재 필수 헤더를 요구하는 곳은 secret key를 받는 익명 링크·통계 API와
	 * {@code Idempotency-Key}를 받는 캠페인 대량 발행·임포트 API이므로, 후자가 빠진 요청에는
	 * 원인과 맞지 않는 안내가 나간다. 헤더별로 문구를 나누려면 {@code exception.getHeaderName()}으로 분기한다.
	 */
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException exception) {
		return badRequest("관리용 secret key 헤더가 필요합니다.");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		return badRequest("요청 본문 형식이 올바르지 않습니다.");
	}

	/**
	 * 링크를 찾지 못한 경우를 404로 바꾼다. 삭제된 링크, 삭제된 프로젝트의 링크, 아예 없는 코드가
	 * 모두 같은 응답으로 합쳐진다. 셋을 구분해 주면 존재 여부 자체가 정보가 되므로 의도적으로 합친 것이다.
	 */
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

	/**
	 * 위험 URL 차단을 400으로 바꾼다. 재시도해도 결과가 같은 영구 거부이므로 5xx가 아니다.
	 */
	@ExceptionHandler(UnsafeUrlException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsafeUrl(UnsafeUrlException exception) {
		// 알려진 위협 URL은 클라이언트가 수정할 수 있는 요청 오류로 응답함.
		log.warn("API request rejected: code={}", URL_THREAT_DETECTED);
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(URL_THREAT_DETECTED, exception.getMessage()));
	}

	/**
	 * 위험 검사를 수행하지 못한 상태를 503으로 바꾼다. URL이 위험하다는 판정이 아니라 판정 자체를 못 한 것이므로,
	 * 클라이언트가 잠시 뒤 다시 시도하면 성공할 수 있다는 뜻을 상태 코드로 구분해 준다.
	 */
	@ExceptionHandler(UrlRiskCheckFailedException.class)
	public ResponseEntity<ApiErrorResponse> handleUrlRiskCheckFailed(UrlRiskCheckFailedException exception) {
		// 외부 검사 불가 상태는 재시도 가능한 서비스 오류로 응답함.
		log.warn("API request unavailable: code={}", URL_CHECK_FAILED);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ApiErrorResponse(URL_CHECK_FAILED, exception.getMessage()));
	}

	@ExceptionHandler(ExternalIdConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleExternalIdConflict(ExternalIdConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("EXTERNAL_ID_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(BatchIdempotencyConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleBatchIdempotencyConflict(BatchIdempotencyConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("IDEMPOTENCY_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(CampaignImportIdempotencyConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleImportIdempotencyConflict(CampaignImportIdempotencyConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("IDEMPOTENCY_CONFLICT", exception.getMessage()));
	}

	@ExceptionHandler(ActiveImportConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleActiveImportConflict(ActiveImportConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("IMPORT_IN_PROGRESS", exception.getMessage()));
	}

	/**
	 * 한도 초과를 429로 바꾸고 {@code Retry-After}에 남은 대기 시간을 초 단위로 담는다.
	 * 클라이언트가 이 헤더를 보고 재시도 간격을 정하므로, 헤더를 빼면 즉시 재시도가 반복돼 부하가 더 커진다.
	 */
	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(RateLimitExceededException exception) {
		log.warn("API request rate limited: retryAfterSeconds={}", exception.getRetryAfterSeconds());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
				.body(new ApiErrorResponse("RATE_LIMIT_EXCEEDED", exception.getMessage()));
	}

	// 매핑되지 않은 경로는 클라이언트 오류입니다. 아래 catch-all 이 이 예외를 삼켜
	// 존재하지 않는 URL 이 전부 500 으로 보고되고 있었습니다.
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException exception) {
		log.debug("No handler for request: path={}", exception.getResourcePath());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorResponse("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
	}

	/**
	 * 위에서 걸리지 않은 모든 예외를 500으로 바꾼다. 예외 메시지를 응답에 넣지 않는 이유는
	 * 여기 도달하는 예외가 위 어느 타입으로도 분류되지 않은 것이라, 그 메시지에 무엇이 담겨 있는지
	 * 보장할 수 없기 때문이다. 원인 파악에 필요한 정보는 스택트레이스와 함께 로그로만 남긴다.
	 *
	 * <p>다만 이 catch-all은 위 핸들러가 없는 예외를 전부 장애로 보고하게 만든다.
	 * 위의 {@code NoResourceFoundException} 처리가 그 사례이므로, 500 급증을 조사할 때는
	 * 먼저 클라이언트 오류가 여기로 흘러들어오고 있지 않은지 확인한다.</p>
	 */
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

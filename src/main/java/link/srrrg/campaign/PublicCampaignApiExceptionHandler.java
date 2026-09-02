package link.srrrg.campaign;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import link.srrrg.campaign.importing.ActiveImportConflictException;
import link.srrrg.campaign.importing.CampaignImportIdempotencyConflictException;
import link.srrrg.common.ratelimit.RateLimitExceededException;
import link.srrrg.link.ExternalIdConflictException;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.management.LinkManagementService;

/**
 * campaign/utm-template 공개 API 전용 오류 형식. 기존 {@code PublicProjectLinkController}의 형식은 건드리지 않는다.
 */
/**
 * campaign/utm-template 공개 API 전용 오류 형식. 기존 {@code PublicProjectLinkController}의 형식은 건드리지 않는다.
 *
 * <p>{@code assignableTypes}로 두 컨트롤러만 대상으로 삼아, 웹 표면이 쓰는 {@code ApiErrorResponse}와
 * 형식이 섞이지 않게 한다. 다만 우선순위를 선언하지 않아 전역 처리기와 기본 순위가 같으므로,
 * 같은 예외 타입을 양쪽에 등록해 두면 어느 형식이 나갈지는 advice 정렬 결과에 달린다.</p>
 *
 * <p>여기 등록되지 않은 예외는 전역 처리기로 넘어가 웹용 형식으로 나간다.
 * 이 표면에 새 예외를 도입하면 반드시 여기에도 등록한다.</p>
 */
@RestControllerAdvice(assignableTypes = {PublicCampaignController.class, PublicUtmTemplateController.class})
class PublicCampaignApiExceptionHandler {

	@ExceptionHandler(PublicApiException.class)
	ResponseEntity<ProblemDetail> handle(PublicApiException exception) {
		return problem(exception.status, exception.code, exception.getMessage(), null);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException exception) {
		return problem(400, "INVALID_REQUEST", exception.getMessage(), null);
	}

	@ExceptionHandler(CampaignNotFoundException.class)
	ResponseEntity<ProblemDetail> handleCampaignNotFound(CampaignNotFoundException exception) {
		return problem(404, "CAMPAIGN_NOT_FOUND", exception.getMessage(), null);
	}

	@ExceptionHandler(SecurityException.class)
	ResponseEntity<ProblemDetail> handleSecurity(SecurityException exception) {
		return problem(403, "PROJECT_ACCESS_DENIED", "프로젝트 접근 권한이 없습니다.", null);
	}

	@ExceptionHandler(ExternalIdConflictException.class)
	ResponseEntity<ProblemDetail> handleExternalIdConflict(ExternalIdConflictException exception) {
		return problem(409, "EXTERNAL_ID_CONFLICT", exception.getMessage(), null);
	}

	@ExceptionHandler(LinkManagementService.IdempotencyConflictException.class)
	ResponseEntity<ProblemDetail> handleLinkIdempotencyConflict(LinkManagementService.IdempotencyConflictException exception) {
		return problem(409, "IDEMPOTENCY_CONFLICT", exception.getMessage(), null);
	}

	@ExceptionHandler(BatchIdempotencyConflictException.class)
	ResponseEntity<ProblemDetail> handleBatchConflict(BatchIdempotencyConflictException exception) {
		return problem(409, "IDEMPOTENCY_CONFLICT", exception.getMessage(), null);
	}

	@ExceptionHandler(CampaignImportIdempotencyConflictException.class)
	ResponseEntity<ProblemDetail> handleImportConflict(CampaignImportIdempotencyConflictException exception) {
		return problem(409, "IDEMPOTENCY_CONFLICT", exception.getMessage(), null);
	}

	@ExceptionHandler(ActiveImportConflictException.class)
	ResponseEntity<ProblemDetail> handleActiveImportConflict(ActiveImportConflictException exception) {
		return problem(409, "IMPORT_IN_PROGRESS", exception.getMessage(), null);
	}

	@ExceptionHandler(UnsafeUrlException.class)
	ResponseEntity<ProblemDetail> handleUnsafeUrl(UnsafeUrlException exception) {
		return problem(400, "URL_THREAT_DETECTED", exception.getMessage(), null);
	}

	@ExceptionHandler(UrlRiskCheckFailedException.class)
	ResponseEntity<ProblemDetail> handleUrlRiskCheckFailed(UrlRiskCheckFailedException exception) {
		return problem(503, "URL_CHECK_FAILED", exception.getMessage(), null);
	}

	@ExceptionHandler(LinkGoneException.class)
	ResponseEntity<ProblemDetail> handleLinkGone(LinkGoneException exception) {
		return problem(410, "LINK_GONE", exception.getMessage(), null);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException exception) {
		return problem(429, "RATE_LIMIT_EXCEEDED", exception.getMessage(), exception.getRetryAfterSeconds());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException exception) {
		return problem(400, "INVALID_REQUEST", "요청 본문 형식이 올바르지 않습니다.", null);
	}

	private ResponseEntity<ProblemDetail> problem(int status, String code, String message, Long retryAfterSeconds) {
		String requestId = UUID.randomUUID().toString();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), message);
		problem.setType(URI.create("https://srrrg.link/problems/" + code.toLowerCase()));
		problem.setTitle("API request failed");
		problem.setProperty("code", code);
		problem.setProperty("requestId", requestId);
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", requestId);
		if (retryAfterSeconds != null) builder.header("Retry-After", String.valueOf(retryAfterSeconds));
		return builder.contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}
}

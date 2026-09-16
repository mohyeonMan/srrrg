package link.srrrg.web.error.handler;

import link.srrrg.web.error.model.PublicApiException;

import java.net.URI;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import link.srrrg.campaign.link.batch.model.BatchIdempotencyConflictException;
import link.srrrg.campaign.model.CampaignNotFoundException;
import link.srrrg.campaign.controller.PublicCampaignController;
import link.srrrg.campaign.link.controller.PublicCampaignLinkController;
import link.srrrg.campaign.link.csv.controller.PublicCampaignCsvController;
import link.srrrg.campaign.utm.controller.PublicCampaignUtmController;
import link.srrrg.utmtemplate.controller.PublicUtmTemplateController;
import link.srrrg.campaign.link.csv.model.ActiveImportConflictException;
import link.srrrg.campaign.link.csv.model.CampaignImportIdempotencyConflictException;
import link.srrrg.campaign.link.csv.model.CampaignImportNotFoundException;
import link.srrrg.common.ratelimit.model.RateLimitExceededException;
import link.srrrg.link.model.ExternalIdConflictException;
import link.srrrg.link.creation.model.LinkIdempotencyConflictException;
import link.srrrg.link.model.LinkGoneException;
import link.srrrg.link.model.LinkCodeConflictException;
import link.srrrg.link.model.LinkNotFoundException;
import link.srrrg.link.risk.model.UnsafeUrlException;
import link.srrrg.link.risk.model.UrlRiskCheckFailedException;
import link.srrrg.link.management.controller.PublicProjectLinkController;
import link.srrrg.statistics.controller.PublicStatisticsController;
import lombok.extern.slf4j.Slf4j;

/**
 * 공개 API 컨트롤러에서 발생한 예외를 RFC 7807 {@link ProblemDetail}로 변환한다.
 * {@code /api/web/**}와 익명 링크 API는 다른 오류 계약을 사용하므로 적용 대상을 클래스 목록으로 제한한다.
 *
 * <p>Spring의 controller advice는 URL 패턴으로 대상을 고를 수 없다. 커스텀 표식 annotation을 만들지 않고
 * 명시적인 {@code assignableTypes} 목록을 사용하며, 새 공개 API 컨트롤러를 추가할 때 이 목록과 계약 테스트를
 * 함께 갱신해야 한다.</p>
 */
@RestControllerAdvice(assignableTypes = {
		PublicCampaignController.class,
		PublicCampaignLinkController.class,
		PublicCampaignCsvController.class,
		PublicCampaignUtmController.class,
		PublicUtmTemplateController.class,
		PublicProjectLinkController.class,
		PublicStatisticsController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class PublicApiExceptionHandler {

	@ExceptionHandler(PublicApiException.class)
	ResponseEntity<ProblemDetail> handle(PublicApiException exception) {
		return problem(exception.status(), exception.code(), exception.getMessage(), null);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException exception) {
		return problem(400, "INVALID_REQUEST", exception.getMessage(), null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getAllErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("요청 값이 올바르지 않습니다.");
		log.debug("Public API request validation failed: errorCount={}",
				exception.getBindingResult().getErrorCount());
		return problem(400, "INVALID_REQUEST", message, null);
	}

	@ExceptionHandler(CampaignNotFoundException.class)
	ResponseEntity<ProblemDetail> handleCampaignNotFound(CampaignNotFoundException exception) {
		return problem(404, "CAMPAIGN_NOT_FOUND", exception.getMessage(), null);
	}

	@ExceptionHandler(CampaignImportNotFoundException.class)
	ResponseEntity<ProblemDetail> handleCampaignImportNotFound(CampaignImportNotFoundException exception) {
		return problem(404, "IMPORT_NOT_FOUND", exception.getMessage(), null);
	}

	@ExceptionHandler(LinkNotFoundException.class)
	ResponseEntity<ProblemDetail> handleLinkNotFound(LinkNotFoundException exception) {
		return problem(404, "LINK_NOT_FOUND", exception.getMessage(), null);
	}

	@ExceptionHandler(LinkCodeConflictException.class)
	ResponseEntity<ProblemDetail> handleLinkCodeConflict(LinkCodeConflictException exception) {
		return problem(409, "LINK_CODE_CONFLICT", exception.getMessage(), null);
	}

	@ExceptionHandler(SecurityException.class)
	ResponseEntity<ProblemDetail> handleSecurity(SecurityException exception) {
		return problem(403, "PROJECT_ACCESS_DENIED", "프로젝트 접근 권한이 없습니다.", null);
	}

	@ExceptionHandler(ExternalIdConflictException.class)
	ResponseEntity<ProblemDetail> handleExternalIdConflict(ExternalIdConflictException exception) {
		return problem(409, "EXTERNAL_ID_CONFLICT", exception.getMessage(), null);
	}

	@ExceptionHandler(LinkIdempotencyConflictException.class)
	ResponseEntity<ProblemDetail> handleLinkIdempotencyConflict(LinkIdempotencyConflictException exception) {
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

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException exception) {
		return problem(400, "INVALID_REQUEST", exception.getHeaderName() + " 헤더가 필요합니다.", null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
		log.error("Unexpected public API error: type={}", exception.getClass().getSimpleName(), exception);
		return problem(500, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", null);
	}

	private ResponseEntity<ProblemDetail> problem(int status, String code, String message, Long retryAfterSeconds) {
		String requestId = UUID.randomUUID().toString();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), message);
		problem.setType(URI.create("https://srrrg.link/problems/" + code.toLowerCase()));
		problem.setTitle("API request failed");
		problem.setProperty("code", code);
		problem.setProperty("requestId", requestId);
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", requestId);
		if (retryAfterSeconds != null) {
			builder.header("Retry-After", String.valueOf(retryAfterSeconds));
		}
		return builder.contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}
}

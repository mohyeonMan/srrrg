package link.srrrg.link.management.controller;

import link.srrrg.project.apikey.model.ApiKeyPrincipal;
import link.srrrg.project.apikey.model.ApiKeyScope;
import link.srrrg.project.controller.ProjectController;
import link.srrrg.project.model.Project;
import link.srrrg.web.error.dto.ApiErrorResponse;
import link.srrrg.web.error.handler.PublicApiExceptionHandler;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import link.srrrg.auth.request.service.ApiKeyRequestAuthorizer;
import link.srrrg.web.error.model.PublicApiException;
import link.srrrg.link.model.Link;
import link.srrrg.link.creation.model.LinkIdempotencyConflictException;
import link.srrrg.link.risk.model.UnsafeUrlException;
import link.srrrg.link.risk.model.UrlRiskCheckFailedException;
import link.srrrg.link.management.service.ProjectLinkService;
import link.srrrg.link.management.dto.CreateLinkRequest;

/**
 * API 키로 호출하는 프로젝트 링크 엔드포인트. 같은 기능의 웹 경로가 {@code ProjectController}에 따로 있고,
 * 두 경로는 인증 방식과 오류 형식이 다르다. 한쪽만 고치면 정책이 어긋난다.
 *
 * <p>오류는 공개 API 공통 처리기인 {@code PublicApiExceptionHandler}가 RFC 7807
 * {@code ProblemDetail}로 바꾼다. 전역 처리기가 쓰는 {@code ApiErrorResponse} 형식과 섞이지 않게
 * 공개 API 컨트롤러에만 적용된다.</p>
 *
 * <p>인가는 {@link ApiKeyRequestAuthorizer}를 반드시 거친다. Spring Security의 인가 규칙은 이 경로를
 * 통과시키므로, 프로젝트 소속과 scope 확인을 빠뜨리면 키만 있으면 남의 프로젝트에 접근할 수 있다.</p>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/links")
@Tag(name = "Project links")
public class PublicProjectLinkController {
	private final ProjectLinkService projectLinkService;
	private final ApiKeyRequestAuthorizer apiKeyRequestAuthorizer;

	PublicProjectLinkController(ProjectLinkService projectLinkService, ApiKeyRequestAuthorizer apiKeyRequestAuthorizer) {
		this.projectLinkService = projectLinkService;
		this.apiKeyRequestAuthorizer = apiKeyRequestAuthorizer;
	}

	@PostMapping
	@Operation(summary = "Create a project link", security = @SecurityRequirement(name = "projectApiKey"))
	ResponseEntity<LinkResponse> create(jakarta.servlet.http.HttpServletRequest servletRequest,
			@PathVariable Long projectId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody CreateLinkRequest request) {
		ApiKeyPrincipal principal = apiKeyRequestAuthorizer.require(servletRequest, projectId, ApiKeyScope.LINKS_WRITE);
		try {
			return ResponseEntity.status(201).body(LinkResponse.from(projectLinkService.createForApiKey(
					principal.keyId(), projectId, idempotencyKey, request)));
		} catch (LinkIdempotencyConflictException exception) {
			throw new PublicApiException(409, "IDEMPOTENCY_CONFLICT", exception.getMessage());
		} catch (UrlRiskCheckFailedException exception) {
			throw new PublicApiException(503, "URL_CHECK_FAILED", exception.getMessage());
		} catch (UnsafeUrlException | IllegalArgumentException exception) {
			throw new PublicApiException(400, "INVALID_REQUEST", exception.getMessage());
		}
	}

	/**
	 * 커서 기반으로 링크를 나눠 준다. offset 대신 마지막 id를 커서로 쓰는 이유는, 목록이 계속 늘어나는 자원이라
	 * offset 방식에서는 페이지 사이에 새 링크가 끼어들면 항목이 중복되거나 건너뛰기 때문이다.
	 *
	 * <p>한 개 더 읽어 다음 페이지 존재 여부를 판단한다. 별도 count 쿼리를 피하기 위한 방법이다.</p>
	 */
	@GetMapping
	@Operation(summary = "List project links", security = @SecurityRequirement(name = "projectApiKey"))
	LinkPageResponse list(jakarta.servlet.http.HttpServletRequest request, @PathVariable Long projectId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.LINKS_READ);
		if (limit < 1 || limit > 100)
			throw new PublicApiException(400, "INVALID_REQUEST", "limit은 1~100 사이여야 합니다.");
		List<Link> results = projectLinkService.listForApiKey(projectId, cursor, limit);
		List<Link> page = results.size() > limit ? results.subList(0, limit) : results;
		Long nextCursor = results.size() > limit ? page.getLast().getId() : null;
		return new LinkPageResponse(page.stream().map(LinkResponse::from).toList(), nextCursor);
	}

	public record LinkPageResponse(List<LinkResponse> items, Long nextCursor) {
	}

	record LinkResponse(String code, String name, String originalUrl, java.time.Instant expiresAt,
			java.time.Instant createdAt) {
		static LinkResponse from(Link link) {
			return new LinkResponse(link.getCode(), link.getName(), link.getOriginalUrl(), link.getExpiresAt(),
					link.getCreatedAt());
		}
	}
}

package link.srrrg.project;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import link.srrrg.project.ApiKeyService.ApiKeyPrincipal;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.LinkManagementService;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/links")
@Tag(name = "Project links")
public class PublicProjectLinkController {
	private final LinkRepository links;
	private final ProjectService projects;
	PublicProjectLinkController(LinkRepository links, ProjectService projects) { this.links = links; this.projects = projects; }
	@PostMapping
	@Operation(summary = "Create a project link", security = @SecurityRequirement(name = "projectApiKey"))
	ResponseEntity<LinkResponse> create(jakarta.servlet.http.HttpServletRequest servletRequest,
			@PathVariable Long projectId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody CreateLinkRequest request) {
		ApiKeyPrincipal principal = principal(servletRequest, projectId, ApiKeyScope.LINKS_WRITE);
		try {
			return ResponseEntity.status(201).body(LinkResponse.from(projects.createProjectLink(
					principal.keyId(), projectId, idempotencyKey, request)));
		} catch (LinkManagementService.IdempotencyConflictException exception) {
			throw new PublicApiException(409, "IDEMPOTENCY_CONFLICT", exception.getMessage());
		} catch (UrlRiskCheckFailedException exception) {
			throw new PublicApiException(503, "URL_CHECK_FAILED", exception.getMessage());
		} catch (UnsafeUrlException | IllegalArgumentException exception) {
			throw new PublicApiException(400, "INVALID_REQUEST", exception.getMessage());
		}
	}
	@GetMapping
	@Operation(summary = "List project links", security = @SecurityRequirement(name = "projectApiKey"))
	LinkPageResponse list(jakarta.servlet.http.HttpServletRequest request, @PathVariable Long projectId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		principal(request, projectId, ApiKeyScope.LINKS_READ);
		if (limit < 1 || limit > 100) throw new PublicApiException(400, "INVALID_REQUEST", "limit은 1~100 사이여야 합니다.");
		List<Link> results = cursor == null
				? links.findByProjectIdAndCampaignIsNullAndDeletedFalseOrderByIdDesc(projectId, org.springframework.data.domain.PageRequest.of(0, limit + 1))
				: links.findByProjectIdAndCampaignIsNullAndDeletedFalseAndIdLessThanOrderByIdDesc(projectId, cursor, org.springframework.data.domain.PageRequest.of(0, limit + 1));
		List<Link> page = results.size() > limit ? results.subList(0, limit) : results;
		Long nextCursor = results.size() > limit ? page.getLast().getId() : null;
		return new LinkPageResponse(page.stream().map(LinkResponse::from).toList(), nextCursor);
	}
	private ApiKeyPrincipal principal(jakarta.servlet.http.HttpServletRequest request, Long projectId, ApiKeyScope scope) {
		ApiKeyPrincipal principal = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (principal == null) throw new PublicApiException(401, "API_KEY_INVALID", "유효한 API key가 필요합니다.");
		if (!principal.projectId().equals(projectId)) throw new PublicApiException(403, "PROJECT_ACCESS_DENIED", "다른 프로젝트의 리소스에는 접근할 수 없습니다.");
		if (!principal.scopes().contains(scope)) throw new PublicApiException(403, "SCOPE_REQUIRED", scope.value() + " scope가 필요합니다.");
		return principal;
	}
	public record LinkPageResponse(List<LinkResponse> items, Long nextCursor) { }
	@ExceptionHandler(PublicApiException.class)
	ResponseEntity<ProblemDetail> handle(PublicApiException exception) {
		String requestId = UUID.randomUUID().toString();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(exception.status), exception.getMessage());
		problem.setType(java.net.URI.create("https://srrrg.link/problems/" + exception.code.toLowerCase()));
		problem.setTitle("API request failed"); problem.setProperty("code", exception.code); problem.setProperty("requestId", requestId);
		return ResponseEntity.status(exception.status).header("X-Request-Id", requestId).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException exception) {
		return handle(new PublicApiException(400, "INVALID_REQUEST", "요청 본문 형식이 올바르지 않습니다."));
	}
	record LinkResponse(String code, String name, String originalUrl, java.time.Instant expiresAt, java.time.Instant createdAt) {
		static LinkResponse from(Link link) { return new LinkResponse(link.getCode(), link.getName(), link.getOriginalUrl(), link.getExpiresAt(), link.getCreatedAt()); }
	}
	static class PublicApiException extends RuntimeException {
		final int status; final String code;
		PublicApiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
	}
}

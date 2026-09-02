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
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.link.management.ProjectLinkService;
import link.srrrg.link.management.dto.CreateLinkRequest;

/**
 * API 키로 호출하는 프로젝트 링크 엔드포인트. 같은 기능의 웹 경로가 {@code ProjectController}에 따로 있고,
 * 두 경로는 인증 방식과 오류 형식이 다르다. 한쪽만 고치면 정책이 어긋난다.
 *
 * <p>오류를 RFC 7807 {@code ProblemDetail}로 돌려주기 위해 이 컨트롤러 안에 예외 처리기를 둔다.
 * 전역 처리기가 쓰는 {@code ApiErrorResponse} 형식과 섞이지 않게 하려는 것이다.</p>
 *
 * <p>인가는 {@link #principal}이 전담한다. Spring Security의 인가 규칙은 이 경로를 통과시키므로,
 * 프로젝트 소속과 scope 확인을 빠뜨리면 키만 있으면 남의 프로젝트에 접근할 수 있다.</p>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/links")
@Tag(name = "Project links")
public class PublicProjectLinkController {
	private final ProjectLinkService projectLinkService;

	PublicProjectLinkController(ProjectLinkService projectLinkService) {
		this.projectLinkService = projectLinkService;
	}

	@PostMapping
	@Operation(summary = "Create a project link", security = @SecurityRequirement(name = "projectApiKey"))
	ResponseEntity<LinkResponse> create(jakarta.servlet.http.HttpServletRequest servletRequest,
			@PathVariable Long projectId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody CreateLinkRequest request) {
		ApiKeyPrincipal principal = principal(servletRequest, projectId, ApiKeyScope.LINKS_WRITE);
		try {
			return ResponseEntity.status(201).body(LinkResponse.from(projectLinkService.createForApiKey(
					principal.keyId(), projectId, idempotencyKey, request)));
		} catch (LinkManagementService.IdempotencyConflictException exception) {
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
		principal(request, projectId, ApiKeyScope.LINKS_READ);
		if (limit < 1 || limit > 100)
			throw new PublicApiException(400, "INVALID_REQUEST", "limit은 1~100 사이여야 합니다.");
		List<Link> results = projectLinkService.listForApiKey(projectId, cursor, limit);
		List<Link> page = results.size() > limit ? results.subList(0, limit) : results;
		Long nextCursor = results.size() > limit ? page.getLast().getId() : null;
		return new LinkPageResponse(page.stream().map(LinkResponse::from).toList(), nextCursor);
	}

	/**
	 * 이 표면의 인가 관문. 세 가지를 순서대로 확인한다. 필터가 주체를 넣었는지, 그 키가 경로의 프로젝트에
	 * 속하는지, 요청에 필요한 scope를 가졌는지다.
	 *
	 * <p>두 번째 검사가 특히 중요하다. 키 자체는 유효하므로 이것이 없으면 아무 프로젝트 id나 경로에 넣어
	 * 남의 자원을 다룰 수 있다.</p>
	 *
	 * @param scope 이 요청에 필요한 권한. 읽기 경로와 쓰기 경로가 서로 다른 값을 넘긴다
	 * @throws PublicApiException 주체가 없으면 401, 프로젝트가 다르거나 scope가 없으면 403
	 */
	private ApiKeyPrincipal principal(jakarta.servlet.http.HttpServletRequest request, Long projectId,
			ApiKeyScope scope) {
		ApiKeyPrincipal principal = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (principal == null)
			throw new PublicApiException(401, "API_KEY_INVALID", "유효한 API key가 필요합니다.");
		if (!principal.projectId().equals(projectId))
			throw new PublicApiException(403, "PROJECT_ACCESS_DENIED", "다른 프로젝트의 리소스에는 접근할 수 없습니다.");
		if (!principal.scopes().contains(scope))
			throw new PublicApiException(403, "SCOPE_REQUIRED", scope.value() + " scope가 필요합니다.");
		return principal;
	}

	public record LinkPageResponse(List<LinkResponse> items, Long nextCursor) {
	}

	@ExceptionHandler(PublicApiException.class)
	ResponseEntity<ProblemDetail> handle(PublicApiException exception) {
		String requestId = UUID.randomUUID().toString();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				org.springframework.http.HttpStatusCode.valueOf(exception.status), exception.getMessage());
		problem.setType(java.net.URI.create("https://srrrg.link/problems/" + exception.code.toLowerCase()));
		problem.setTitle("API request failed");
		problem.setProperty("code", exception.code);
		problem.setProperty("requestId", requestId);
		return ResponseEntity.status(exception.status).header("X-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException exception) {
		return handle(new PublicApiException(400, "INVALID_REQUEST", "요청 본문 형식이 올바르지 않습니다."));
	}

	record LinkResponse(String code, String name, String originalUrl, java.time.Instant expiresAt,
			java.time.Instant createdAt) {
		static LinkResponse from(Link link) {
			return new LinkResponse(link.getCode(), link.getName(), link.getOriginalUrl(), link.getExpiresAt(),
					link.getCreatedAt());
		}
	}

	/**
	 * 이 표면 전용 오류 신호. 상태 코드와 오류 코드를 함께 담아 아래 처리기가 ProblemDetail로 변환한다.
	 * 전역 처리기가 잡는 예외 타입과 분리해 두어야 응답 형식이 섞이지 않는다.
	 */
	static class PublicApiException extends RuntimeException {
		final int status;
		final String code;

		PublicApiException(int status, String code, String message) {
			super(message);
			this.status = status;
			this.code = code;
		}
	}
}

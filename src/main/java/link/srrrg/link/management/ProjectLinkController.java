package link.srrrg.link.management;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import link.srrrg.auth.SrrrgPrincipal;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.ProjectLinkResponse;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import lombok.RequiredArgsConstructor;

/**
 * 로그인한 사용자가 프로젝트 링크를 관리하는 웹 API 경계다.
 * HTTP 요청·응답과 인증 주체 변환만 맡고, 프로젝트 역할과 링크 소유권 검사는 {@link ProjectLinkService}에 위임한다.
 */
@RestController
@RequestMapping("/api/web/projects/{projectId}/links")
@RequiredArgsConstructor
public class ProjectLinkController {
	private static final String SECRET_KEY_HEADER = "X-Srrrg-Secret-Key";
	private final ProjectLinkService projectLinkService;

	@PostMapping
	public ResponseEntity<ProjectLinkResponse> create(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @Valid @RequestBody CreateLinkRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ProjectLinkResponse.from(projectLinkService.createForWeb(principal.userId(), projectId, request)));
	}

	@GetMapping("/{code}")
	public LinkManagementResponse detail(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @PathVariable String code) {
		return projectLinkService.detailForWeb(principal.userId(), projectId, code);
	}

	@PatchMapping("/{code}")
	public LinkManagementResponse update(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @PathVariable String code, @RequestBody UpdateLinkRequest request) {
		return projectLinkService.updateForWeb(principal.userId(), projectId, code, request);
	}

	@DeleteMapping("/{code}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @PathVariable String code) {
		projectLinkService.deleteForWeb(principal.userId(), projectId, code);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 익명 링크를 프로젝트로 편입한다. 로그인 주체 외에도 링크의 secret key를 함께 받아야 한다.
	 */
	@PostMapping("/{code}/claim")
	public ResponseEntity<Void> claim(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @PathVariable String code,
			@RequestHeader(SECRET_KEY_HEADER) String secretKey) {
		projectLinkService.claimAnonymousForWeb(principal.userId(), projectId, code, secretKey);
		return ResponseEntity.noContent().build();
	}
}

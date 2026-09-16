package link.srrrg.project.subdomain.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.project.model.Project;
import link.srrrg.project.subdomain.service.ProjectSubdomainService;
import lombok.RequiredArgsConstructor;

/** 프로젝트 서브도메인의 조회, 선점, 활성화와 해제 계약을 담당한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectSubdomainController {
	private final ProjectSubdomainService subdomains;

	@GetMapping("/projects/{projectId}/subdomain")
	public SubdomainResponse subdomain(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		return SubdomainResponse.from(subdomains.get(principal.userId(), projectId));
	}

	@PutMapping("/projects/{projectId}/subdomain")
	public SubdomainResponse claim(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@Valid @RequestBody ChangeSubdomainRequest request) {
		return SubdomainResponse.from(subdomains.claim(principal.userId(), projectId, request.subdomain()));
	}

	@PatchMapping("/projects/{projectId}/subdomain/activation")
	public SubdomainResponse activate(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@Valid @RequestBody SubdomainActivationRequest request) {
		return SubdomainResponse.from(subdomains.setEnabled(principal.userId(), projectId, request.enabled()));
	}

	@DeleteMapping("/projects/{projectId}/subdomain")
	public SubdomainResponse release(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		return SubdomainResponse.from(subdomains.release(principal.userId(), projectId));
	}

	public record ChangeSubdomainRequest(@NotBlank @Size(min = 3, max = 63) String subdomain) { }
	public record SubdomainActivationRequest(boolean enabled) { }
	public record SubdomainResponse(String subdomain, boolean enabled) {
		static SubdomainResponse from(Project project) {
			return new SubdomainResponse(project.getSubdomain(), project.isSubdomainEnabled());
		}
	}
}

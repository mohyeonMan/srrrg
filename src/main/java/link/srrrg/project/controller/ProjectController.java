package link.srrrg.project.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.campaign.controller.CampaignController.CampaignResponse;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.link.management.dto.ProjectLinkResponse;
import link.srrrg.link.management.service.ProjectLinkService;
import link.srrrg.project.membership.model.ProjectMember;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.project.membership.service.ProjectMemberService;
import link.srrrg.project.model.Project;
import link.srrrg.project.service.ProjectService;
import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 자체의 생명주기와 첫 화면 구성을 담당한다. 멤버, 초대, API 키와 서브도메인은
 * 각 기능의 컨트롤러가 같은 URL 계약을 유지하며 처리한다.
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectController {
	private final ProjectService projectService;
	private final ProjectLinkService projectLinkService;
	private final ProjectMemberService projectMemberService;
	private final CampaignService campaignService;

	@GetMapping("/projects")
	public List<ProjectResponse> myProjects(@AuthenticationPrincipal SrrrgPrincipal principal) {
		return projectMemberService.myMemberships(principal.userId()).stream().map(ProjectResponse::from).toList();
	}

	@PostMapping("/projects")
	public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal SrrrgPrincipal principal,
			@Valid @RequestBody CreateProjectRequest request) {
		Project project = projectService.create(principal.userId(), request.name(), request.subdomain());
		return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project, ProjectRole.OWNER));
	}

	@GetMapping("/projects/{projectId}")
	public ProjectResponse detail(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		return ProjectResponse.from(projectService.detail(principal.userId(), projectId));
	}

	@PatchMapping("/projects/{projectId}")
	public ProjectResponse rename(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@Valid @RequestBody RenameProjectRequest request) {
		return ProjectResponse.from(projectService.rename(principal.userId(), projectId, request.name()), ProjectRole.OWNER);
	}

	@DeleteMapping("/projects/{projectId}")
	public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		projectService.delete(principal.userId(), projectId);
		return ResponseEntity.noContent().build();
	}

	/** 프로젝트 화면의 독립 링크와 캠페인을 각 소유 서비스에서 읽어 화면 응답으로 조립한다. */
	@GetMapping("/projects/{projectId}/overview")
	public ProjectOverviewResponse overview(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		List<CampaignResponse> campaigns = campaignService.list(principal.userId(), projectId, null, 100).stream()
				.map(CampaignResponse::from).toList();
		return new ProjectOverviewResponse(
				projectLinkService.listForWeb(principal.userId(), projectId).stream().map(ProjectLinkResponse::from).toList(),
				campaigns);
	}

	public record CreateProjectRequest(@NotBlank @Size(max = 100) String name,
			@Size(min = 3, max = 63) String subdomain) { }
	public record RenameProjectRequest(@NotBlank @Size(max = 100) String name) { }
	public record ProjectResponse(Long id, String name, String subdomain, boolean subdomainEnabled, ProjectRole role) {
		public static ProjectResponse from(ProjectMember member) { return from(member.getProject(), member.getRole()); }
		public static ProjectResponse from(Project project, ProjectRole role) {
			return new ProjectResponse(project.getId(), project.getName(), project.getSubdomain(),
					project.isSubdomainEnabled(), role);
		}
	}
	public record ProjectOverviewResponse(List<ProjectLinkResponse> standaloneLinks, List<CampaignResponse> campaigns) { }
}

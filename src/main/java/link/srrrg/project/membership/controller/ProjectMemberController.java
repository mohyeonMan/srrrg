package link.srrrg.project.membership.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.project.membership.model.ProjectMember;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.project.membership.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;

/** 프로젝트 멤버 조회와 역할 변경, 제외 흐름의 HTTP 계약을 담당한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectMemberController {
	private final ProjectMemberService projectMemberService;

	@GetMapping("/projects/{projectId}/members")
	public List<MemberResponse> members(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		return projectMemberService.projectMembers(principal.userId(), projectId).stream().map(MemberResponse::from).toList();
	}

	@PatchMapping("/projects/{projectId}/members/{memberId}")
	public ResponseEntity<Void> changeRole(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@PathVariable Long memberId, @Valid @RequestBody ChangeRoleRequest request) {
		projectMemberService.changeMemberRole(principal.userId(), projectId, memberId, request.role());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/projects/{projectId}/members/{memberId}")
	public ResponseEntity<Void> remove(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@PathVariable Long memberId) {
		projectMemberService.removeMember(principal.userId(), projectId, memberId);
		return ResponseEntity.noContent().build();
	}

	public record ChangeRoleRequest(@NotNull ProjectRole role) { }
	public record MemberResponse(Long userId, String displayName, ProjectRole role) {
		static MemberResponse from(ProjectMember member) {
			return new MemberResponse(member.getUser().getId(), member.getUser().getDisplayName(), member.getRole());
		}
	}
}

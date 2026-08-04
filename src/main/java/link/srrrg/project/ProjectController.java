package link.srrrg.project;

import java.time.Instant;
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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.SrrrgPrincipal;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectController {
	private final ProjectService projects;

	@GetMapping("/projects") public List<ProjectResponse> myProjects(@AuthenticationPrincipal SrrrgPrincipal p) { return projects.myMemberships(p.userId()).stream().map(ProjectResponse::from).toList(); }
	@PostMapping("/projects") public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal SrrrgPrincipal p, @Valid @RequestBody CreateProjectRequest request) { Project project = projects.create(p.userId(), request.name()); return ResponseEntity.status(HttpStatus.CREATED).body(new ProjectResponse(project.getId(), project.getName(), ProjectRole.OWNER)); }
	@GetMapping("/projects/{projectId}/members") public List<MemberResponse> members(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { return projects.projectMembers(p.userId(), projectId).stream().map(MemberResponse::from).toList(); }
	@GetMapping("/projects/{projectId}/invitations") public List<InvitationResponse> invitations(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { return projects.projectInvitations(p.userId(), projectId).stream().map(InvitationResponse::from).toList(); }
	@PostMapping("/projects/{projectId}/invitations") public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @Valid @RequestBody InviteRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(projects.invite(p.userId(), projectId, request.email(), request.role()))); }
	@PostMapping("/invitations/{token}/accept") public ResponseEntity<Void> accept(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable String token) { projects.accept(p.userId(), token); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/invitations/{invitationId}") public ResponseEntity<Void> cancel(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long invitationId) { projects.cancel(p.userId(), invitationId); return ResponseEntity.noContent().build(); }
	@PostMapping("/invitations/{invitationId}/resend") public InvitationResponse resend(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long invitationId) { return InvitationResponse.from(projects.resend(p.userId(), invitationId)); }
	@PatchMapping("/projects/{projectId}/members/{memberId}") public ResponseEntity<Void> changeRole(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @PathVariable Long memberId, @Valid @RequestBody ChangeRoleRequest request) { projects.changeMemberRole(p.userId(), projectId, memberId, request.role()); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/projects/{projectId}/members/{memberId}") public ResponseEntity<Void> remove(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @PathVariable Long memberId) { projects.removeMember(p.userId(), projectId, memberId); return ResponseEntity.noContent().build(); }
	@PostMapping("/projects/{projectId}/links/import") public ResponseEntity<Void> importLink(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @Valid @RequestBody ImportLinkRequest request) { projects.importAnonymousLink(p.userId(), projectId, request.code(), request.secretKey()); return ResponseEntity.noContent().build(); }

	public record CreateProjectRequest(@NotBlank @Size(max = 100) String name) { }
	public record InviteRequest(@Email @NotBlank @Size(max = 320) String email, @NotNull ProjectRole role) { }
	public record ChangeRoleRequest(@NotNull ProjectRole role) { }
	public record ImportLinkRequest(@NotBlank @Size(max = 6) String code, @NotBlank @Size(max = 100) String secretKey) { }
	public record ProjectResponse(Long id, String name, ProjectRole role) { static ProjectResponse from(ProjectMember member) { return new ProjectResponse(member.getProject().getId(), member.getProject().getName(), member.getRole()); } }
	public record MemberResponse(Long userId, String displayName, ProjectRole role) { static MemberResponse from(ProjectMember member) { return new MemberResponse(member.getUser().getId(), member.getUser().getDisplayName(), member.getRole()); } }
	public record InvitationResponse(Long id, String email, ProjectRole role, Instant expiresAt) { static InvitationResponse from(ProjectInvitation invitation) { return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(), invitation.getExpiresAt()); } }
}

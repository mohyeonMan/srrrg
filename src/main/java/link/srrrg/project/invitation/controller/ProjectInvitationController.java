package link.srrrg.project.invitation.controller;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.project.invitation.model.ProjectInvitation;
import link.srrrg.project.invitation.service.ProjectInvitationService;
import link.srrrg.project.membership.model.ProjectRole;
import lombok.RequiredArgsConstructor;

/** 프로젝트 초대의 발급, 조회, 수락, 취소와 재전송 계약을 담당한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectInvitationController {
	private final ProjectInvitationService projectInvitationService;

	@GetMapping("/projects/{projectId}/invitations")
	public List<InvitationResponse> invitations(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		return projectInvitationService.list(principal.userId(), projectId).stream().map(InvitationResponse::from).toList();
	}

	@PostMapping("/projects/{projectId}/invitations")
	public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @Valid @RequestBody InviteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(
				projectInvitationService.invite(principal.userId(), projectId, request.email(), request.role())));
	}

	@PostMapping("/invitations/{token}/accept")
	public AcceptInvitationResponse accept(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable String token) {
		ProjectInvitationService.AcceptedInvitation result = projectInvitationService.accept(principal.userId(), token);
		return new AcceptInvitationResponse(result.projectId(), result.alreadyMember());
	}

	@DeleteMapping("/invitations/{invitationId}")
	public ResponseEntity<Void> cancel(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long invitationId) {
		projectInvitationService.cancel(principal.userId(), invitationId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/invitations/{invitationId}/resend")
	public InvitationResponse resend(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long invitationId) {
		return InvitationResponse.from(projectInvitationService.resend(principal.userId(), invitationId));
	}

	public record InviteRequest(@Email @NotBlank @Size(max = 320) String email, @NotNull ProjectRole role) { }
	public record AcceptInvitationResponse(Long projectId, boolean alreadyMember) { }
	public record InvitationResponse(Long id, String email, ProjectRole role, Instant expiresAt) {
		static InvitationResponse from(ProjectInvitation invitation) {
			return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(), invitation.getExpiresAt());
		}
	}
}

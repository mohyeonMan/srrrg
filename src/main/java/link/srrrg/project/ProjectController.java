package link.srrrg.project;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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
import link.srrrg.link.Link;
import link.srrrg.link.management.dto.CreateLinkRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectController {
	private static final String SECRET_KEY_HEADER = "X-Srrrg-Secret-Key";
	private final ProjectService projects;
	private final ApiKeyService apiKeys;

	@GetMapping("/projects") public List<ProjectResponse> myProjects(@AuthenticationPrincipal SrrrgPrincipal p) { return projects.myMemberships(p.userId()).stream().map(ProjectResponse::from).toList(); }
	@PostMapping("/projects") public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal SrrrgPrincipal p, @Valid @RequestBody CreateProjectRequest request) { Project project = projects.create(p.userId(), request.name(), request.slug()); return ResponseEntity.status(HttpStatus.CREATED).body(new ProjectResponse(project.getId(), project.getName(), project.getSlug(), ProjectRole.OWNER)); }
	@GetMapping("/projects/{projectId}") public ProjectResponse detail(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { return ProjectResponse.from(projects.detail(p.userId(), projectId)); }
	@PatchMapping("/projects/{projectId}") public ProjectResponse rename(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @Valid @RequestBody RenameProjectRequest request) { Project project = projects.rename(p.userId(), projectId, request.name()); return new ProjectResponse(project.getId(), project.getName(), project.getSlug(), ProjectRole.OWNER); }
	@DeleteMapping("/projects/{projectId}") public ResponseEntity<Void> archive(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { projects.archive(p.userId(), projectId); return ResponseEntity.noContent().build(); }
	@GetMapping("/projects/{projectId}/overview") public ProjectOverviewResponse overview(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { return new ProjectOverviewResponse(projects.projectLinks(p.userId(), projectId).stream().map(ProjectLinkResponse::from).toList(), List.of()); }
	@PostMapping("/projects/{projectId}/links") public ResponseEntity<ProjectLinkResponse> createLink(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @Valid @RequestBody CreateLinkRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(ProjectLinkResponse.from(projects.createProjectLink(p.userId(), projectId, request))); }
	@GetMapping("/projects/{projectId}/members") public List<MemberResponse> members(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { return projects.projectMembers(p.userId(), projectId).stream().map(MemberResponse::from).toList(); }
	@GetMapping("/projects/{projectId}/invitations") public List<InvitationResponse> invitations(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { return projects.projectInvitations(p.userId(), projectId).stream().map(InvitationResponse::from).toList(); }
	@PostMapping("/projects/{projectId}/invitations") public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @Valid @RequestBody InviteRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(projects.invite(p.userId(), projectId, request.email(), request.role()))); }
	@PostMapping("/invitations/{token}/accept") public AcceptInvitationResponse accept(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable String token) { ProjectService.AcceptedInvitation result = projects.accept(p.userId(), token); return new AcceptInvitationResponse(result.projectId(), result.alreadyMember()); }
	@DeleteMapping("/invitations/{invitationId}") public ResponseEntity<Void> cancel(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long invitationId) { projects.cancel(p.userId(), invitationId); return ResponseEntity.noContent().build(); }
	@PostMapping("/invitations/{invitationId}/resend") public InvitationResponse resend(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long invitationId) { return InvitationResponse.from(projects.resend(p.userId(), invitationId)); }
	@PatchMapping("/projects/{projectId}/members/{memberId}") public ResponseEntity<Void> changeRole(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @PathVariable Long memberId, @Valid @RequestBody ChangeRoleRequest request) { projects.changeMemberRole(p.userId(), projectId, memberId, request.role()); return ResponseEntity.noContent().build(); }
	@DeleteMapping("/projects/{projectId}/members/{memberId}") public ResponseEntity<Void> remove(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @PathVariable Long memberId) { projects.removeMember(p.userId(), projectId, memberId); return ResponseEntity.noContent().build(); }
	@PostMapping("/projects/{projectId}/links/{code}/claim") public ResponseEntity<Void> claimLink(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @PathVariable String code, @org.springframework.web.bind.annotation.RequestHeader(SECRET_KEY_HEADER) String secretKey) { projects.importAnonymousLink(p.userId(), projectId, code, secretKey); return ResponseEntity.noContent().build(); }
	@GetMapping("/projects/{projectId}/api-keys") public List<ApiKeyResponse> apiKeys(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) { return apiKeys.list(p.userId(), projectId).stream().map(ApiKeyResponse::from).toList(); }
	@PostMapping("/projects/{projectId}/api-keys") public ResponseEntity<CreatedApiKeyResponse> createApiKey(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @Valid @RequestBody CreateApiKeyRequest request) { ApiKeyService.CreatedKey created = apiKeys.create(p.userId(), projectId, request.name(), request.scopes().stream().map(ApiKeyScope::fromValue).collect(java.util.stream.Collectors.toSet()), request.expiresAt()); return ResponseEntity.status(HttpStatus.CREATED).body(CreatedApiKeyResponse.from(created)); }
	@DeleteMapping("/projects/{projectId}/api-keys/{keyId}") public ResponseEntity<Void> revokeApiKey(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId, @PathVariable Long keyId) { apiKeys.revoke(p.userId(), projectId, keyId); return ResponseEntity.noContent().build(); }

	public record CreateProjectRequest(@NotBlank @Size(max = 100) String name, @Size(min = 3, max = 63) String slug) { }
	public record RenameProjectRequest(@NotBlank @Size(max = 100) String name) { }
	public record InviteRequest(@Email @NotBlank @Size(max = 320) String email, @NotNull ProjectRole role) { }
	public record ChangeRoleRequest(@NotNull ProjectRole role) { }
	public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name, @NotNull Set<@NotBlank String> scopes, Instant expiresAt) { }
	public record AcceptInvitationResponse(Long projectId, boolean alreadyMember) { }
	public record ProjectResponse(Long id, String name, String slug, ProjectRole role) { static ProjectResponse from(ProjectMember member) { return new ProjectResponse(member.getProject().getId(), member.getProject().getName(), member.getProject().getSlug(), member.getRole()); } }
	public record ProjectOverviewResponse(List<ProjectLinkResponse> standaloneLinks, List<Object> campaigns) { }
	public record ProjectLinkResponse(String code, String originalUrl, Instant expiresAt, long accessCount, long redirectCount) { static ProjectLinkResponse from(Link link) { return new ProjectLinkResponse(link.getCode(), link.getOriginalUrl(), link.getExpiresAt(), link.getAccessCount(), link.getRedirectCount()); } }
	public record MemberResponse(Long userId, String displayName, ProjectRole role) { static MemberResponse from(ProjectMember member) { return new MemberResponse(member.getUser().getId(), member.getUser().getDisplayName(), member.getRole()); } }
	public record InvitationResponse(Long id, String email, ProjectRole role, Instant expiresAt) { static InvitationResponse from(ProjectInvitation invitation) { return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(), invitation.getExpiresAt()); } }
	public record ApiKeyResponse(Long id, String name, String keyPrefix, Set<String> scopes, Instant createdAt, Instant lastUsedAt, Instant expiresAt, Instant revokedAt) { static ApiKeyResponse from(ProjectApiKey key) { return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.getScopes().stream().map(ApiKeyScope::value).collect(java.util.stream.Collectors.toSet()), key.getCreatedAt(), key.getLastUsedAt(), key.getExpiresAt(), key.getRevokedAt()); } }
	public record CreatedApiKeyResponse(Long id, String key, String keyPrefix, Set<String> scopes, Instant expiresAt) { static CreatedApiKeyResponse from(ApiKeyService.CreatedKey created) { ProjectApiKey key = created.key(); return new CreatedApiKeyResponse(key.getId(), created.rawKey(), key.getKeyPrefix(), key.getScopes().stream().map(ApiKeyScope::value).collect(java.util.stream.Collectors.toSet()), key.getExpiresAt()); } }
}

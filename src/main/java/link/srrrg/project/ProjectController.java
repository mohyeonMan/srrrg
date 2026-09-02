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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.SrrrgPrincipal;
import link.srrrg.campaign.CampaignController.CampaignResponse;
import link.srrrg.campaign.CampaignService;
import link.srrrg.link.Link;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import lombok.RequiredArgsConstructor;

/**
 * 화면이 호출하는 프로젝트 관리 API. 프로젝트, 서브도메인, 링크, 멤버, 초대, API 키가 모두 여기 모여 있다.
 *
 * <p>이 클래스는 요청을 서비스로 넘기고 응답 형태만 만든다. 권한 확인은 하지 않고 전부
 * {@code ProjectService}, {@code ProjectMemberService}, {@code ProjectInvitationService}, {@code ApiKeyService}가
 * 수행하므로, 새 엔드포인트를 추가할 때
 * 여기에 검사를 넣는 것이 아니라 서비스 메서드가 역할을 요구하는지 확인해야 한다.</p>
 *
 * <p>주체는 항상 {@code @AuthenticationPrincipal}에서 온다. 요청 본문이나 경로로 사용자 id를 받지 않으므로
 * 남의 계정을 지정할 수 없다.</p>
 *
 * <p>API 키를 쓰는 같은 기능의 경로가 {@code PublicProjectLinkController}에 따로 있다.
 * 링크 생성 정책을 바꿀 때는 두 경로를 함께 확인한다.</p>
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class ProjectController {
	private static final String SECRET_KEY_HEADER = "X-Srrrg-Secret-Key";
	private final ProjectService projects;
	private final ProjectMemberService projectMembers;
	private final ProjectInvitationService projectInvitations;
	private final ApiKeyService apiKeys;
	private final CampaignService campaigns;

	@GetMapping("/projects")
	public List<ProjectResponse> myProjects(@AuthenticationPrincipal SrrrgPrincipal p) {
		return projectMembers.myMemberships(p.userId()).stream().map(ProjectResponse::from).toList();
	}

	/**
	 * 프로젝트를 만든다. 만든 사람이 곧 OWNER이므로 응답 역할을 조회 없이 고정으로 채운다.
	 */
	@PostMapping("/projects")
	public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal SrrrgPrincipal p,
			@Valid @RequestBody CreateProjectRequest request) {
		Project project = projects.create(p.userId(), request.name(), request.subdomain());
		return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project, ProjectRole.OWNER));
	}

	@GetMapping("/projects/{projectId}")
	public ProjectResponse detail(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) {
		return ProjectResponse.from(projects.detail(p.userId(), projectId));
	}

	@PatchMapping("/projects/{projectId}")
	public ProjectResponse rename(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@Valid @RequestBody RenameProjectRequest request) {
		return ProjectResponse.from(projects.rename(p.userId(), projectId, request.name()), ProjectRole.OWNER);
	}

	@DeleteMapping("/projects/{projectId}")
	public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) {
		projects.delete(p.userId(), projectId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 프로젝트 첫 화면에 필요한 단일 링크와 캠페인 목록을 한 번에 돌려준다.
	 * 화면이 두 번 호출하지 않게 묶은 것이며, 캠페인은 상한을 두고 잘라 온다.
	 * 두 조회가 각각 권한을 확인하므로 여기서 따로 검사하지 않는다.
	 */
	@GetMapping("/projects/{projectId}/overview")
	public ProjectOverviewResponse overview(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) {
		List<CampaignResponse> campaignSummaries = campaigns.list(p.userId(), projectId, null, 100).stream()
				.map(CampaignResponse::from).toList();
		return new ProjectOverviewResponse(
				projects.projectLinks(p.userId(), projectId).stream().map(ProjectLinkResponse::from).toList(),
				campaignSummaries);
	}

	@GetMapping("/projects/{projectId}/subdomain")
	public SubdomainResponse subdomain(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) {
		return SubdomainResponse.from(projects.projectDomain(p.userId(), projectId));
	}

	/**
	 * 서브도메인을 선점한다. 선점만으로는 링크가 그 호스트로 발급되지 않으며,
	 * 아래 활성화 엔드포인트를 따로 호출해야 실제로 쓰인다.
	 */
	@PutMapping("/projects/{projectId}/subdomain")
	public SubdomainResponse claimSubdomain(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@Valid @RequestBody ChangeSubdomainRequest request) {
		return SubdomainResponse.from(projects.claimSubdomain(p.userId(), projectId, request.subdomain()));
	}

	@PatchMapping("/projects/{projectId}/subdomain/activation")
	public SubdomainResponse activateSubdomain(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@Valid @RequestBody SubdomainActivationRequest request) {
		return SubdomainResponse.from(projects.setSubdomainEnabled(p.userId(), projectId, request.enabled()));
	}

	/**
	 * 선점을 해제한다. 그 호스트로 이미 발급된 링크는 열리지 않게 되므로 되돌리기 어려운 변경이다.
	 */
	@DeleteMapping("/projects/{projectId}/subdomain")
	public SubdomainResponse releaseSubdomain(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) {
		return SubdomainResponse.from(projects.releaseSubdomain(p.userId(), projectId));
	}

	@PostMapping("/projects/{projectId}/links")
	public ResponseEntity<ProjectLinkResponse> createLink(@AuthenticationPrincipal SrrrgPrincipal p,
			@PathVariable Long projectId, @Valid @RequestBody CreateLinkRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ProjectLinkResponse.from(projects.createProjectLink(p.userId(), projectId, request)));
	}

	@GetMapping("/projects/{projectId}/links/{code}")
	public LinkManagementResponse link(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@PathVariable String code) {
		return projects.projectLink(p.userId(), projectId, code);
	}

	@PatchMapping("/projects/{projectId}/links/{code}")
	public LinkManagementResponse updateLink(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@PathVariable String code, @RequestBody UpdateLinkRequest request) {
		return projects.updateProjectLink(p.userId(), projectId, code, request);
	}

	@DeleteMapping("/projects/{projectId}/links/{code}")
	public ResponseEntity<Void> deleteLink(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@PathVariable String code) {
		projects.deleteProjectLink(p.userId(), projectId, code);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/projects/{projectId}/members")
	public List<MemberResponse> members(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) {
		return projectMembers.projectMembers(p.userId(), projectId).stream().map(MemberResponse::from).toList();
	}

	@GetMapping("/projects/{projectId}/invitations")
	public List<InvitationResponse> invitations(@AuthenticationPrincipal SrrrgPrincipal p,
			@PathVariable Long projectId) {
		return projectInvitations.list(p.userId(), projectId).stream().map(InvitationResponse::from).toList();
	}

	@PostMapping("/projects/{projectId}/invitations")
	public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal SrrrgPrincipal p,
			@PathVariable Long projectId, @Valid @RequestBody InviteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(InvitationResponse.from(projectInvitations.invite(p.userId(), projectId, request.email(), request.role())));
	}

	/**
	 * 초대를 수락한다. 인증이 필요하므로 로그인 후에만 호출되며, 초대 화면은 로그인 전에도 열린다.
	 * 이미 멤버인 경우도 성공으로 처리하고 그 사실을 응답에 담아, 화면이 안내 문구를 고를 수 있게 한다.
	 */
	@PostMapping("/invitations/{token}/accept")
	public AcceptInvitationResponse accept(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable String token) {
		ProjectInvitationService.AcceptedInvitation result = projectInvitations.accept(p.userId(), token);
		return new AcceptInvitationResponse(result.projectId(), result.alreadyMember());
	}

	@DeleteMapping("/invitations/{invitationId}")
	public ResponseEntity<Void> cancel(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long invitationId) {
		projectInvitations.cancel(p.userId(), invitationId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/invitations/{invitationId}/resend")
	public InvitationResponse resend(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long invitationId) {
		return InvitationResponse.from(projectInvitations.resend(p.userId(), invitationId));
	}

	@PatchMapping("/projects/{projectId}/members/{memberId}")
	public ResponseEntity<Void> changeRole(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@PathVariable Long memberId, @Valid @RequestBody ChangeRoleRequest request) {
		projectMembers.changeMemberRole(p.userId(), projectId, memberId, request.role());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/projects/{projectId}/members/{memberId}")
	public ResponseEntity<Void> remove(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@PathVariable Long memberId) {
		projectMembers.removeMember(p.userId(), projectId, memberId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 비회원으로 만든 링크를 이 프로젝트로 옮긴다. 로그인 세션만으로는 부족하고,
	 * 그 링크의 secret key를 헤더로 함께 제시해야 한다. 소유권을 넘기는 처리라 두 자격을 모두 요구한다.
	 */
	@PostMapping("/projects/{projectId}/links/{code}/claim")
	public ResponseEntity<Void> claimLink(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@PathVariable String code,
			@org.springframework.web.bind.annotation.RequestHeader(SECRET_KEY_HEADER) String secretKey) {
		projects.importAnonymousLink(p.userId(), projectId, code, secretKey);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/projects/{projectId}/api-keys")
	public List<ApiKeyResponse> apiKeys(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId) {
		return apiKeys.list(p.userId(), projectId).stream().map(ApiKeyResponse::from).toList();
	}

	/**
	 * API 키를 발급한다. 요청의 scope 문자열을 enum으로 바꾸면서 알 수 없는 값은 예외가 되므로,
	 * 오타가 난 scope가 조용히 빠진 채 키가 만들어지지 않는다.
	 * 응답에 담기는 키 원문은 이 한 번만 나간다.
	 */
	@PostMapping("/projects/{projectId}/api-keys")
	public ResponseEntity<CreatedApiKeyResponse> createApiKey(@AuthenticationPrincipal SrrrgPrincipal p,
			@PathVariable Long projectId, @Valid @RequestBody CreateApiKeyRequest request) {
		ApiKeyService.CreatedKey created = apiKeys.create(p.userId(), projectId, request.name(),
				request.scopes().stream().map(ApiKeyScope::fromValue).collect(java.util.stream.Collectors.toSet()),
				request.expiresAt());
		return ResponseEntity.status(HttpStatus.CREATED).body(CreatedApiKeyResponse.from(created));
	}

	@DeleteMapping("/projects/{projectId}/api-keys/{keyId}")
	public ResponseEntity<Void> revokeApiKey(@AuthenticationPrincipal SrrrgPrincipal p, @PathVariable Long projectId,
			@PathVariable Long keyId) {
		apiKeys.revoke(p.userId(), projectId, keyId);
		return ResponseEntity.noContent().build();
	}

	public record CreateProjectRequest(@NotBlank @Size(max = 100) String name,
			@Size(min = 3, max = 63) String subdomain) {
	}

	public record RenameProjectRequest(@NotBlank @Size(max = 100) String name) {
	}

	public record InviteRequest(@Email @NotBlank @Size(max = 320) String email, @NotNull ProjectRole role) {
	}

	public record ChangeRoleRequest(@NotNull ProjectRole role) {
	}

	public record ChangeSubdomainRequest(@NotBlank @Size(min = 3, max = 63) String subdomain) {
	}

	public record SubdomainActivationRequest(boolean enabled) {
	}

	public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name, @NotNull Set<@NotBlank String> scopes,
			Instant expiresAt) {
	}

	public record AcceptInvitationResponse(Long projectId, boolean alreadyMember) {
	}

	public record ProjectResponse(Long id, String name, String subdomain, boolean subdomainEnabled, ProjectRole role) {
		static ProjectResponse from(ProjectMember member) {
			return from(member.getProject(), member.getRole());
		}

		static ProjectResponse from(Project project, ProjectRole role) {
			return new ProjectResponse(project.getId(), project.getName(), project.getSubdomain(),
					project.isSubdomainEnabled(), role);
		}
	}

	public record ProjectOverviewResponse(List<ProjectLinkResponse> standaloneLinks, List<CampaignResponse> campaigns) {
	}

	public record SubdomainResponse(String subdomain, boolean enabled) {
		static SubdomainResponse from(Project project) {
			return new SubdomainResponse(project.getSubdomain(), project.isSubdomainEnabled());
		}
	}

	public record ProjectLinkResponse(String code, String subdomain, String name, String originalUrl, Instant expiresAt,
			Instant createdAt) {
		static ProjectLinkResponse from(Link link) {
			return new ProjectLinkResponse(link.getCode(), link.getSubdomain(), link.getName(), link.getOriginalUrl(),
					link.getExpiresAt(), link.getCreatedAt());
		}
	}

	public record MemberResponse(Long userId, String displayName, ProjectRole role) {
		static MemberResponse from(ProjectMember member) {
			return new MemberResponse(member.getUser().getId(), member.getUser().getDisplayName(), member.getRole());
		}
	}

	public record InvitationResponse(Long id, String email, ProjectRole role, Instant expiresAt) {
		static InvitationResponse from(ProjectInvitation invitation) {
			return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
					invitation.getExpiresAt());
		}
	}

	public record ApiKeyResponse(Long id, String name, String keyPrefix, Set<String> scopes, Instant createdAt,
			Instant lastUsedAt, Instant expiresAt, Instant revokedAt) {
		static ApiKeyResponse from(ProjectApiKey key) {
			return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(),
					key.getScopes().stream().map(ApiKeyScope::value).collect(java.util.stream.Collectors.toSet()),
					key.getCreatedAt(), key.getLastUsedAt(), key.getExpiresAt(), key.getRevokedAt());
		}
	}

	public record CreatedApiKeyResponse(Long id, String key, String keyPrefix, Set<String> scopes, Instant expiresAt) {
		static CreatedApiKeyResponse from(ApiKeyService.CreatedKey created) {
			ProjectApiKey key = created.key();
			return new CreatedApiKeyResponse(key.getId(), created.rawKey(), key.getKeyPrefix(),
					key.getScopes().stream().map(ApiKeyScope::value).collect(java.util.stream.Collectors.toSet()),
					key.getExpiresAt());
		}
	}
}

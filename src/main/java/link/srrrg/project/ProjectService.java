package link.srrrg.project;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.domain.ProjectDomain;
import link.srrrg.domain.ProjectDomainService;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkCodeConflictException;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.SecretKeyManager;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.UpdateLinkRequest;

@Service
public class ProjectService {
	private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	private static final String SLUG_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectInvitationRepository invitations;
	private final UserRepository users;
	private final LinkRepository links;
	private final SecureRandomStringGenerator random;
	private final InvitationEmailSender emailSender;
	private final SecretKeyManager secretKeys;
	private final LinkManagementService linkManagement;
	private final ProjectDomainService domains;
	private final RateLimitService rateLimitService;
	private final String baseUrl;

	public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectInvitationRepository invitations,
			UserRepository users, LinkRepository links, SecureRandomStringGenerator random, InvitationEmailSender emailSender, SecretKeyManager secretKeys,
			LinkManagementService linkManagement, ProjectDomainService domains, RateLimitService rateLimitService,
			@Value("${srrrg.base-url}") String baseUrl) {
		this.projects = projects; this.members = members; this.invitations = invitations; this.users = users; this.links = links;
		this.random = random; this.emailSender = emailSender; this.secretKeys = secretKeys; this.linkManagement = linkManagement; this.domains = domains;
		this.rateLimitService = rateLimitService; this.baseUrl = baseUrl;
	}

	@Transactional
	public void ensurePersonalProject(Long userId) {
		if (members.findByIdUserIdAndProjectArchivedAtIsNull(userId).isEmpty()) create(userId, "내 프로젝트", null);
	}

	@Transactional
	public Project create(Long userId, String name) {
		return create(userId, name, null);
	}

	@Transactional
	public Project create(Long userId, String name, String requestedSlug) {
		if (members.countByIdUserIdAndRoleAndProjectArchivedAtIsNull(userId, ProjectRole.OWNER) >= 5) throw new IllegalStateException("소유 프로젝트는 최대 5개까지 만들 수 있습니다.");
		User user = user(userId);
		Project project;
		try {
			project = projects.saveAndFlush(Project.create(validName(name), slug(requestedSlug), user));
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("이미 사용 중인 프로젝트 slug입니다.", exception);
		}
		domains.create(project);
		members.save(new ProjectMember(project, user, ProjectRole.OWNER));
		return project;
	}

	@Transactional(readOnly = true)
	public List<ProjectMember> myMemberships(Long userId) { return members.findByIdUserIdAndProjectArchivedAtIsNull(userId); }
	@Transactional(readOnly = true)
	public ProjectMember detail(Long userId, Long projectId) { return requireRole(userId, projectId, ProjectRole.VIEWER); }
	@Transactional(readOnly = true)
	public List<Link> projectLinks(Long userId, Long projectId) { requireRole(userId, projectId, ProjectRole.VIEWER); return links.findByProjectIdAndCampaignIsNullAndDeletedFalseOrderByIdDesc(projectId); }
	@Transactional(readOnly = true)
	public List<ProjectMember> projectMembers(Long userId, Long projectId) { requireRole(userId, projectId, ProjectRole.VIEWER); return members.findByIdProjectId(projectId); }
	@Transactional(readOnly = true)
	public List<ProjectInvitation> projectInvitations(Long userId, Long projectId) { requireRole(userId, projectId, ProjectRole.OWNER); return invitations.findByProjectIdAndCancelledAtIsNullAndAcceptedAtIsNull(projectId); }
	@Transactional(readOnly = true)
	public ProjectDomain projectDomain(Long userId, Long projectId) { requireRole(userId, projectId, ProjectRole.VIEWER); return domains.get(projectId); }

	@Transactional
	public Project rename(Long userId, Long projectId, String name) {
		Project project = requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		project.rename(validName(name));
		return project;
	}

	@Transactional
	public void archive(Long userId, Long projectId) {
		requireRole(userId, projectId, ProjectRole.OWNER).getProject().archive();
	}

	@Transactional
	public ProjectInvitation invite(Long userId, Long projectId, String email, ProjectRole role) {
		requireRole(userId, projectId, ProjectRole.OWNER);
		if (role == ProjectRole.OWNER) throw new IllegalArgumentException("초대 역할은 EDITOR 또는 VIEWER여야 합니다.");
		String normalizedEmail = validEmail(email);
		Project project = project(projectId);
		users.findByEmail(normalizedEmail)
				.filter(user -> members.findByIdProjectIdAndIdUserId(projectId, user.getId()).isPresent())
				.ifPresent(user -> { throw new IllegalArgumentException("이미 프로젝트 멤버인 이메일입니다."); });
		invitations.findByProjectIdAndEmailAndCancelledAtIsNullAndAcceptedAtIsNull(projectId, normalizedEmail)
				.ifPresent(existing -> {
					if (existing.isUsable(Instant.now())) throw new IllegalArgumentException("이미 활성 상태인 초대가 있습니다.");
					existing.cancel();
				});
		String rawToken = random.generate(TOKEN_CHARS, 43);
		ProjectInvitation invitation;
		try {
			invitation = invitations.saveAndFlush(ProjectInvitation.create(project, normalizedEmail, role,
					InvitationTokenHash.sha256(rawToken), Instant.now().plus(Duration.ofDays(7))));
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("이미 활성 상태인 초대가 있습니다.", exception);
		}
		emailSender.send(normalizedEmail, project.getName(), baseUrl + "/invitations/" + rawToken);
		return invitation;
	}

	@Transactional
	public ProjectInvitation resend(Long userId, Long invitationId) {
		ProjectInvitation old = invitation(invitationId);
		requireRole(userId, old.getProject().getId(), ProjectRole.OWNER);
		if (old.getAcceptedAt() != null) throw new IllegalArgumentException("이미 수락된 초대입니다.");
		old.cancel();
		return invite(userId, old.getProject().getId(), old.getEmail(), old.getRole());
	}

	@Transactional
	public void cancel(Long userId, Long invitationId) {
		ProjectInvitation invitation = invitation(invitationId);
		requireRole(userId, invitation.getProject().getId(), ProjectRole.OWNER);
		invitation.cancel();
	}

	@Transactional
	public AcceptedInvitation accept(Long userId, String rawToken) {
		ProjectInvitation invitation = invitations.findByTokenHash(InvitationTokenHash.sha256(rawToken))
				.orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));
		Long projectId = invitation.getProject().getId();
		if (invitation.getProject().getArchivedAt() != null) throw new IllegalStateException("사용할 수 없는 초대입니다.");
		if (!invitation.isUsable(Instant.now())) throw new IllegalStateException("사용할 수 없는 초대입니다.");
		if (members.findByIdProjectIdAndIdUserId(projectId, userId).isPresent()) {
			invitation.accept();
			return new AcceptedInvitation(projectId, true);
		}
		members.save(new ProjectMember(invitation.getProject(), user(userId), invitation.getRole()));
		invitation.accept();
		return new AcceptedInvitation(projectId, false);
	}

	@Transactional(readOnly = true)
	public InvitationPreview invitationPreview(String rawToken) {
		return invitations.findByTokenHash(InvitationTokenHash.sha256(rawToken))
				.filter(invitation -> invitation.getProject().getArchivedAt() == null && invitation.isUsable(Instant.now()))
				.map(invitation -> new InvitationPreview(
						true, invitation.getProject().getName(), invitation.getRole(), invitation.getExpiresAt()))
				.orElseGet(() -> new InvitationPreview(false, null, null, null));
	}

	@Transactional
	public void changeMemberRole(Long actorId, Long projectId, Long memberId, ProjectRole role) {
		requireRole(actorId, projectId, ProjectRole.OWNER);
		ProjectMember member = members.lockByProjectAndUser(projectId, memberId).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));
		if (member.getRole() == ProjectRole.OWNER && role != ProjectRole.OWNER && members.countByIdProjectIdAndRole(projectId, ProjectRole.OWNER) == 1) throw new IllegalStateException("마지막 OWNER는 강등할 수 없습니다.");
		member.changeRole(role);
	}

	@Transactional
	public void removeMember(Long actorId, Long projectId, Long memberId) {
		requireRole(actorId, projectId, ProjectRole.OWNER);
		ProjectMember member = members.lockByProjectAndUser(projectId, memberId).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));
		if (member.getRole() == ProjectRole.OWNER && members.countByIdProjectIdAndRole(projectId, ProjectRole.OWNER) == 1) throw new IllegalStateException("마지막 OWNER는 제거할 수 없습니다.");
		members.delete(member);
	}

	@Transactional
	public void importAnonymousLink(Long userId, Long projectId, String code, String secret) {
		requireRole(userId, projectId, ProjectRole.EDITOR);
		Link link = links.lockAnonymousByCode(code).orElseThrow(() -> new IllegalArgumentException("링크를 찾을 수 없습니다."));
		if (link.getProject() != null || link.getSecretKeyHash() == null || !secretKeys.matches(secret, link.getSecretKeyHash())) throw new IllegalArgumentException("링크를 찾을 수 없습니다.");
		link.assignToProject(project(projectId), domains.get(projectId), user(userId));
		try {
			links.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new LinkCodeConflictException();
		}
	}

	public Link createProjectLink(Long userId, Long projectId, CreateLinkRequest request) {
		ProjectMember membership = requireRole(userId, projectId, ProjectRole.EDITOR);
		return linkManagement.createForProject(request, membership.getProject(), domains.get(projectId), membership.getUser());
	}

	public Link createProjectLink(Long apiKeyId, Long projectId, String idempotencyKey, CreateLinkRequest request) {
		rateLimitService.checkApiKeyWrite(apiKeyId);
		String normalizedKey = validIdempotencyKey(idempotencyKey);
		String requestHash = normalizedKey == null ? null : InvitationTokenHash.sha256(
				String.valueOf(request.originalUrl()) + "\n" + String.valueOf(request.expiresAt()));
		return linkManagement.createForProject(request, project(projectId), domains.get(projectId), null,
				normalizedKey == null ? null : apiKeyId, normalizedKey, requestHash);
	}

	@Transactional
	public void deleteProjectLink(Long userId, Long projectId, String code) {
		requireRole(userId, projectId, ProjectRole.EDITOR);
		projectLink(projectId, code).delete();
	}

	@Transactional(readOnly = true)
	public LinkManagementResponse projectLink(Long userId, Long projectId, String code) {
		ProjectMember member = requireRole(userId, projectId, ProjectRole.VIEWER);
		return linkManagement.projectManagementResponse(projectLink(projectId, code), member.getRole() != ProjectRole.VIEWER);
	}

	@Transactional
	public LinkManagementResponse updateProjectLink(Long userId, Long projectId, String code, UpdateLinkRequest request) {
		requireRole(userId, projectId, ProjectRole.EDITOR);
		return linkManagement.updateProjectLink(projectLink(projectId, code), request);
	}

	private Link projectLink(Long projectId, String code) {
		Link link = links.findByProjectIdAndCode(projectId, code).orElseThrow(LinkNotFoundException::new);
		if (link.isDeleted()) throw new LinkGoneException(LinkGoneException.Reason.DELETED);
		return link;
	}

	private ProjectMember requireRole(Long userId, Long projectId, ProjectRole minimum) {
		ProjectMember membership = members.findByIdProjectIdAndIdUserId(projectId, userId).orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
		if (membership.getProject().getArchivedAt() != null) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		if (membership.getRole().ordinal() > minimum.ordinal()) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		return membership;
	}
	private Project project(Long id) { Project project = projects.findById(id).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다.")); if (project.getArchivedAt() != null) throw new IllegalArgumentException("프로젝트를 찾을 수 없습니다."); return project; }
	private User user(Long id) { return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")); }
	private String validName(String value) { if (value == null || value.trim().isEmpty() || value.trim().length() > 100) throw new IllegalArgumentException("프로젝트 이름은 1~100자로 입력하세요."); return value.trim(); }
	private String validIdempotencyKey(String value) { if (value == null) return null; if (!value.matches("[A-Za-z0-9._:-]{1,100}")) throw new IllegalArgumentException("Idempotency-Key가 올바르지 않습니다."); return value; }
	private String slug(String requested) {
		if (requested == null || requested.isBlank()) {
			for (int attempt = 0; attempt < 5; attempt++) {
				String generated = "p-" + random.generate(SLUG_CHARS, 8);
				if (!projects.existsBySlug(generated)) return generated;
			}
			throw new IllegalStateException("프로젝트 slug를 생성하지 못했습니다.");
		}
		String normalized = requested.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() < 3 || normalized.length() > 63 || !normalized.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])")
				|| domains.isReservedSlug(normalized)) throw new IllegalArgumentException("프로젝트 slug가 올바르지 않습니다.");
		if (projects.existsBySlug(normalized)) throw new IllegalArgumentException("이미 사용 중인 프로젝트 slug입니다.");
		return normalized;
	}
	private String validEmail(String value) { if (value == null || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || value.length() > 320) throw new IllegalArgumentException("올바른 이메일을 입력하세요."); return value.trim().toLowerCase(java.util.Locale.ROOT); }
	private ProjectInvitation invitation(Long id) { return invitations.findById(id).orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다.")); }

	public record AcceptedInvitation(Long projectId, boolean alreadyMember) { }
	public record InvitationPreview(boolean available, String projectName, ProjectRole role, Instant expiresAt) { }
}

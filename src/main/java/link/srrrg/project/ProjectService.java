package link.srrrg.project;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.SecretKeyManager;

@Service
public class ProjectService {
	private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectInvitationRepository invitations;
	private final UserRepository users;
	private final LinkRepository links;
	private final SecureRandomStringGenerator random;
	private final InvitationEmailSender emailSender;
	private final SecretKeyManager secretKeys;
	private final String baseUrl;

	public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectInvitationRepository invitations,
			UserRepository users, LinkRepository links, SecureRandomStringGenerator random, InvitationEmailSender emailSender, SecretKeyManager secretKeys,
			@Value("${srrrg.base-url}") String baseUrl) {
		this.projects = projects; this.members = members; this.invitations = invitations; this.users = users; this.links = links;
		this.random = random; this.emailSender = emailSender; this.secretKeys = secretKeys; this.baseUrl = baseUrl;
	}

	@Transactional
	public void ensurePersonalProject(Long userId) {
		if (members.findByIdUserId(userId).isEmpty()) create(userId, "내 프로젝트");
	}

	@Transactional
	public Project create(Long userId, String name) {
		if (members.countByIdUserIdAndRole(userId, ProjectRole.OWNER) >= 5) throw new IllegalStateException("소유 프로젝트는 최대 5개까지 만들 수 있습니다.");
		User user = user(userId);
		Project project = projects.saveAndFlush(Project.create(validName(name)));
		members.save(new ProjectMember(project, user, ProjectRole.OWNER));
		return project;
	}

	@Transactional(readOnly = true)
	public List<ProjectMember> myMemberships(Long userId) { return members.findByIdUserId(userId); }
	@Transactional(readOnly = true)
	public List<ProjectMember> projectMembers(Long userId, Long projectId) { requireRole(userId, projectId, ProjectRole.VIEWER); return members.findByIdProjectId(projectId); }
	@Transactional(readOnly = true)
	public List<ProjectInvitation> projectInvitations(Long userId, Long projectId) { requireRole(userId, projectId, ProjectRole.OWNER); return invitations.findByProjectId(projectId); }

	@Transactional
	public ProjectInvitation invite(Long userId, Long projectId, String email, ProjectRole role) {
		requireRole(userId, projectId, ProjectRole.OWNER);
		if (role == ProjectRole.OWNER) throw new IllegalArgumentException("초대 역할은 EDITOR 또는 VIEWER여야 합니다.");
		String normalizedEmail = validEmail(email);
		Project project = project(projectId);
		String rawToken = random.generate(TOKEN_CHARS, 43);
		ProjectInvitation invitation = invitations.save(ProjectInvitation.create(project, normalizedEmail, role,
				InvitationTokenHash.sha256(rawToken), Instant.now().plus(Duration.ofDays(7))));
		emailSender.send(normalizedEmail, project.getName(), baseUrl + "/invitations/" + rawToken);
		return invitation;
	}

	@Transactional
	public ProjectInvitation resend(Long userId, Long invitationId) {
		ProjectInvitation old = invitation(invitationId);
		requireRole(userId, old.getProject().getId(), ProjectRole.OWNER);
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
	public void accept(Long userId, String rawToken) {
		ProjectInvitation invitation = invitations.findByTokenHash(InvitationTokenHash.sha256(rawToken))
				.orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));
		if (!invitation.isUsable(Instant.now())) throw new IllegalStateException("사용할 수 없는 초대입니다.");
		if (members.findByIdProjectIdAndIdUserId(invitation.getProject().getId(), userId).isPresent()) throw new IllegalStateException("이미 프로젝트 멤버입니다.");
		members.save(new ProjectMember(invitation.getProject(), user(userId), invitation.getRole()));
		invitation.accept();
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
		Link link = links.findByCode(code).orElseThrow(() -> new IllegalArgumentException("링크를 찾을 수 없습니다."));
		if (link.getProject() != null || link.getSecretKeyHash() == null || !secretKeys.matches(secret, link.getSecretKeyHash())) throw new IllegalArgumentException("링크를 찾을 수 없습니다.");
		link.assignToProject(project(projectId));
	}

	private void requireRole(Long userId, Long projectId, ProjectRole minimum) {
		ProjectMember membership = members.findByIdProjectIdAndIdUserId(projectId, userId).orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
		if (membership.getRole().ordinal() > minimum.ordinal()) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
	}
	private Project project(Long id) { return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다.")); }
	private User user(Long id) { return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")); }
	private String validName(String value) { if (value == null || value.trim().isEmpty() || value.trim().length() > 100) throw new IllegalArgumentException("프로젝트 이름은 1~100자로 입력하세요."); return value.trim(); }
	private String validEmail(String value) { if (value == null || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || value.length() > 320) throw new IllegalArgumentException("올바른 이메일을 입력하세요."); return value.trim().toLowerCase(java.util.Locale.ROOT); }
	private ProjectInvitation invitation(Long id) { return invitations.findById(id).orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다.")); }
}

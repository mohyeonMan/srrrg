package link.srrrg.project;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;

/**
 * 프로젝트 초대의 발급, 재발급, 취소, 수락과 공개 미리보기를 소유한다.
 * OWNER 권한과 토큰 검증부터 멤버십 생성까지 이 경계 안에서 처리한다.
 *
 * <p>메일 발송은 초대 저장 트랜잭션 안에서 실행한다. 발송 실패 시 초대도 롤백하는 현재 정책을 유지하지만,
 * 발송 성공 뒤 커밋이 실패하면 사용할 수 없는 링크가 전달될 수 있다.</p>
 */
@Service
public class ProjectInvitationService {
	private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

	private final ProjectInvitationRepository invitations;
	private final ProjectMemberRepository members;
	private final ProjectRepository projects;
	private final UserRepository users;
	private final ProjectAccessService projectAccess;
	private final SecureRandomStringGenerator random;
	private final InvitationEmailSender emailSender;
	private final String baseUrl;

	public ProjectInvitationService(ProjectInvitationRepository invitations, ProjectMemberRepository members,
			ProjectRepository projects, UserRepository users, ProjectAccessService projectAccess,
			SecureRandomStringGenerator random, InvitationEmailSender emailSender,
			@Value("${srrrg.base-url}") String baseUrl) {
		this.invitations = invitations;
		this.members = members;
		this.projects = projects;
		this.users = users;
		this.projectAccess = projectAccess;
		this.random = random;
		this.emailSender = emailSender;
		this.baseUrl = baseUrl;
	}

	/** 초대 대상 이메일이 노출되므로 OWNER만 대기 중인 초대를 조회한다. */
	@Transactional(readOnly = true)
	public List<ProjectInvitation> list(Long userId, Long projectId) {
		projectAccess.requireRole(userId, projectId, ProjectRole.OWNER);
		return invitations.findByProjectIdAndCancelledAtIsNullAndAcceptedAtIsNull(projectId);
	}

	/**
	 * 프로젝트에 사용자를 초대하고 원문 토큰을 메일로 보낸다. 원문은 저장하지 않는다.
	 * 만료된 초대는 취소해 활성 초대 유일 제약을 비운 뒤 새 초대를 만든다.
	 */
	@Transactional
	public ProjectInvitation invite(Long userId, Long projectId, String email, ProjectRole role) {
		projectAccess.requireRole(userId, projectId, ProjectRole.OWNER);
		if (role == ProjectRole.OWNER) {
			throw new IllegalArgumentException("초대 역할은 EDITOR 또는 VIEWER여야 합니다.");
		}
		String normalizedEmail = validEmail(email);
		Project project = project(projectId);
		users.findByEmail(normalizedEmail)
				.filter(user -> members.findActiveByProjectAndUser(projectId, user.getId()).isPresent())
				.ifPresent(user -> {
					throw new IllegalArgumentException("이미 프로젝트 멤버인 이메일입니다.");
				});
		invitations.findByProjectIdAndEmailAndCancelledAtIsNullAndAcceptedAtIsNull(projectId, normalizedEmail)
				.ifPresent(existing -> {
					if (existing.isUsable(Instant.now())) {
						throw new IllegalArgumentException("이미 활성 상태인 초대가 있습니다.");
					}
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

	/** 기존 초대를 취소하고 같은 대상과 역할로 새 토큰을 발급한다. */
	@Transactional
	public ProjectInvitation resend(Long userId, Long invitationId) {
		ProjectInvitation old = invitation(invitationId);
		projectAccess.requireRole(userId, old.getProject().getId(), ProjectRole.OWNER);
		if (old.getAcceptedAt() != null) {
			throw new IllegalArgumentException("이미 수락된 초대입니다.");
		}
		old.cancel();
		return invite(userId, old.getProject().getId(), old.getEmail(), old.getRole());
	}

	@Transactional
	public void cancel(Long userId, Long invitationId) {
		ProjectInvitation invitation = invitation(invitationId);
		projectAccess.requireRole(userId, invitation.getProject().getId(), ProjectRole.OWNER);
		invitation.cancel();
	}

	/**
	 * 원문 토큰을 가진 로그인 사용자를 프로젝트 멤버로 추가한다. 초대 이메일과 계정 이메일은 비교하지 않는다.
	 * 이미 멤버라면 역할을 바꾸지 않고 초대만 소진한다.
	 */
	@Transactional
	public AcceptedInvitation accept(Long userId, String rawToken) {
		ProjectInvitation invitation = invitations.findActiveByTokenHash(InvitationTokenHash.sha256(rawToken))
				.orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));
		Long projectId = invitation.getProject().getId();
		if (!invitation.isUsable(Instant.now())) {
			throw new IllegalStateException("사용할 수 없는 초대입니다.");
		}
		if (members.findActiveByProjectAndUser(projectId, userId).isPresent()) {
			invitation.accept();
			return new AcceptedInvitation(projectId, true);
		}
		members.save(new ProjectMember(invitation.getProject(), user(userId), invitation.getRole()));
		invitation.accept();
		return new AcceptedInvitation(projectId, false);
	}

	/** 유효하지 않은 토큰은 프로젝트 정보를 숨긴 {@code available=false} 결과로 합친다. */
	@Transactional(readOnly = true)
	public InvitationPreview preview(String rawToken) {
		return invitations.findActiveByTokenHash(InvitationTokenHash.sha256(rawToken))
				.filter(invitation -> invitation.isUsable(Instant.now()))
				.map(invitation -> new InvitationPreview(
						true, invitation.getProject().getName(), invitation.getRole(), invitation.getExpiresAt()))
				.orElseGet(() -> new InvitationPreview(false, null, null, null));
	}

	private Project project(Long id) {
		return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
	}

	private User user(Long id) {
		return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	private String validEmail(String value) {
		if (value == null || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || value.length() > 320) {
			throw new IllegalArgumentException("올바른 이메일을 입력하세요.");
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private ProjectInvitation invitation(Long id) {
		return invitations.findById(id).orElseThrow(() -> new IllegalArgumentException("초대를 찾을 수 없습니다."));
	}

	public record AcceptedInvitation(Long projectId, boolean alreadyMember) {
	}

	public record InvitationPreview(boolean available, String projectName, ProjectRole role, Instant expiresAt) {
	}
}

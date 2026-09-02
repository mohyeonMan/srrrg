package link.srrrg.project;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.campaign.UtmTemplateService;
import link.srrrg.common.ratelimit.RateLimitService;
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

/**
 * 프로젝트와 그 구성원, 프로젝트 소속 링크의 유스케이스를 조율한다.
 *
 * <p>거의 모든 공개 메서드가 {@link ProjectAccessService#requireRole}로 시작한다. 프로젝트 자원은 멤버십이 있어야 접근할 수
 * 있고 역할에 따라 허용 범위가 다르므로, 조회 전에 권한을 확인하는 이 순서를 지켜야 한다.
 * 먼저 조회한 뒤 권한을 보면 존재 여부가 응답 차이로 새어 나간다.</p>
 *
 * <p>웹 경로와 API key 경로가 짝을 이룬다. 웹은 사용자 id로 멤버십을 확인하지만, API key는 키 자체가
 * 프로젝트에 묶여 있어 멤버십이 없다. 그래서 API key용 메서드는 역할 검사 대신 호출자
 * (컨트롤러)가 키의 프로젝트와 scope를 확인한 뒤 부른다. 한쪽만 고치면 두 경로의 정책이 어긋난다.</p>
 *
 * <p>삭제된 프로젝트는 {@code @SoftDelete}와 조회 시 fetch join으로 걸러진다. 멤버십을 찾을 때
 * 프로젝트를 함께 조인하므로, 프로젝트가 삭제되면 멤버십 자체가 조회되지 않아 모든 접근이 권한 없음이 된다.</p>
 */
@Service
public class ProjectService {
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectAccessService projectAccess;
	private final UserRepository users;
	private final LinkRepository links;
	private final SecretKeyManager secretKeys;
	private final LinkManagementService linkManagement;
	private final ProjectDomainService domains;
	private final RateLimitService rateLimitService;
	private final UtmTemplateService utmTemplates;

	public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectAccessService projectAccess,
			UserRepository users, LinkRepository links, SecretKeyManager secretKeys,
			LinkManagementService linkManagement, ProjectDomainService domains, RateLimitService rateLimitService,
			UtmTemplateService utmTemplates) {
		this.projects = projects;
		this.members = members;
		this.projectAccess = projectAccess;
		this.users = users;
		this.links = links;
		this.secretKeys = secretKeys;
		this.linkManagement = linkManagement;
		this.domains = domains;
		this.rateLimitService = rateLimitService;
		this.utmTemplates = utmTemplates;
	}

	/**
	 * 소속 프로젝트가 하나도 없는 사용자에게 기본 프로젝트를 만들어 준다. 로그인이 확정된 직후 호출되며,
	 * 프로젝트가 없으면 링크를 만들 곳이 없어 첫 화면이 비어 버리기 때문이다.
	 * 이미 어느 프로젝트에든 속해 있으면 아무것도 하지 않는다.
	 */
	@Transactional
	public void ensurePersonalProject(Long userId) {
		if (members.findActiveByUserId(userId).isEmpty())
			create(userId, "내 프로젝트", null);
	}

	@Transactional
	public Project create(Long userId, String name) {
		return create(userId, name, null);
	}

	/**
	 * 프로젝트를 만들고 요청자를 OWNER로 등록한 뒤 기본 UTM 템플릿까지 함께 만든다.
	 * 세 가지가 한 트랜잭션에 묶여 있어야 템플릿 없는 프로젝트가 남지 않는다.
	 *
	 * @param requestedSlug 원하는 서브도메인. {@code null}이면 서브도메인 없이 만든다
	 * @throws IllegalStateException 소유 프로젝트 수 상한을 넘은 경우
	 * @throws IllegalArgumentException 이름이나 서브도메인이 올바르지 않거나 이미 사용 중인 경우
	 */
	@Transactional
	public Project create(Long userId, String name, String requestedSlug) {
		// 한 사람이 소유할 수 있는 프로젝트 수를 제한한다. 서브도메인을 무제한 선점하는 것을 막기 위한 상한이다.
		if (members.countActiveByUserIdAndRole(userId, ProjectRole.OWNER) >= 5)
			throw new IllegalStateException("소유 프로젝트는 최대 5개까지 만들 수 있습니다.");
		User user = user(userId);
		Project project;
		try {
			// 사전 중복 확인과 저장 사이에 다른 요청이 같은 서브도메인을 가져갈 수 있다.
			// 최종 판정은 아래 catch가 받는 DB 유일 제약이며, 그 실패를 사용자 오류 메시지로 바꾼다.
			project = projects.saveAndFlush(Project.create(validName(name), optionalSubdomain(requestedSlug), user));
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("이미 사용 중인 서브도메인입니다.", exception);
		}
		members.save(new ProjectMember(project, user, ProjectRole.OWNER));
		utmTemplates.createDefault(project);
		return project;
	}

	@Transactional(readOnly = true)
	public List<ProjectMember> myMemberships(Long userId) {
		return members.findActiveByUserId(userId);
	}

	@Transactional(readOnly = true)
	public ProjectMember detail(Long userId, Long projectId) {
		return projectAccess.requireRole(userId, projectId, ProjectRole.VIEWER);
	}

	@Transactional(readOnly = true)
	public List<Link> projectLinks(Long userId, Long projectId) {
		projectAccess.requireRole(userId, projectId, ProjectRole.VIEWER);
		return links.findByProjectIdAndCampaignIsNullOrderByIdDesc(projectId);
	}

	@Transactional(readOnly = true)
	public List<ProjectMember> projectMembers(Long userId, Long projectId) {
		projectAccess.requireRole(userId, projectId, ProjectRole.VIEWER);
		return members.findByIdProjectId(projectId);
	}

	@Transactional(readOnly = true)
	public Project projectDomain(Long userId, Long projectId) {
		return projectAccess.requireRole(userId, projectId, ProjectRole.VIEWER).getProject();
	}

	@Transactional
	public Project rename(Long userId, Long projectId, String name) {
		Project project = projectAccess.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		project.rename(validName(name));
		return project;
	}

	/**
	 * 서브도메인을 선점한다. 선점만 하고 활성화는 따로 하는 것은, 도메인 설정이 실제로 준비되기 전에
	 * 링크가 그 호스트로 발급되는 것을 막기 위해서다.
	 *
	 * <p>중복 확인을 미리 하고도 저장 실패를 다시 잡는 것은 그 사이 다른 요청이 끼어들 수 있어서다.
	 * 자기 자신이 이미 가진 값이면 중복으로 보지 않는다.</p>
	 */
	@Transactional
	public Project claimSubdomain(Long userId, Long projectId, String requestedSubdomain) {
		Project project = projectAccess.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		String subdomain = normalizedSubdomain(requestedSubdomain);
		if (!subdomain.equals(project.getSubdomain()) && projects.existsBySubdomain(subdomain)) {
			throw new IllegalArgumentException("이미 사용 중인 서브도메인입니다.");
		}
		project.claimSubdomain(subdomain);
		try {
			return projects.saveAndFlush(project);
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("이미 사용 중인 서브도메인입니다.", exception);
		}
	}

	@Transactional
	public Project setSubdomainEnabled(Long userId, Long projectId, boolean enabled) {
		Project project = projectAccess.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		project.setSubdomainEnabled(enabled);
		return project;
	}

	/**
	 * 서브도메인 선점을 푼다. 그 호스트로 이미 발급된 링크는 접근 경로를 잃으므로,
	 * 되돌릴 수 없는 변경으로 보고 OWNER만 실행할 수 있다.
	 */
	@Transactional
	public Project releaseSubdomain(Long userId, Long projectId) {
		Project project = projectAccess.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		project.releaseSubdomain();
		return project;
	}

	/**
	 * 프로젝트를 soft delete한다. 링크와 멤버십을 지우지 않는 것이 중요하다.
	 * 조회 경로가 모두 프로젝트를 조인하므로, 프로젝트 한 행만 지워도 소속 자원 전체가 접근 불가가 된다.
	 * 통계 이벤트가 링크를 참조하고 있어 물리 삭제도 하지 않는다.
	 */
	@Transactional
	public void delete(Long userId, Long projectId) {
		projectAccess.requireRole(userId, projectId, ProjectRole.OWNER);
		// @SoftDelete가 걸려 있어 bulk delete는 deleted_at을 찍는 UPDATE로 번역된다.
		projects.softDeleteById(projectId);
	}

	/**
	 * 멤버 역할을 바꾼다. 마지막 OWNER의 강등을 막는 것이 핵심이다. 아무도 OWNER가 아닌 프로젝트는
	 * 멤버 관리와 삭제가 불가능해져 되돌릴 방법이 없다.
	 *
	 * <p>대상 멤버를 행 잠금으로 읽는 이유는 이 검사 때문이다. 두 OWNER가 서로를 동시에 강등하면
	 * 각자 다른 하나가 남아 있다고 보고 둘 다 통과해 OWNER가 사라진다.</p>
	 */
	@Transactional
	public void changeMemberRole(Long actorId, Long projectId, Long memberId, ProjectRole role) {
		projectAccess.requireRole(actorId, projectId, ProjectRole.OWNER);
		ProjectMember member = members.lockByProjectAndUser(projectId, memberId)
				.orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));
		if (member.getRole() == ProjectRole.OWNER && role != ProjectRole.OWNER
				&& members.countByIdProjectIdAndRole(projectId, ProjectRole.OWNER) == 1)
			throw new IllegalStateException("마지막 OWNER는 강등할 수 없습니다.");
		member.changeRole(role);
	}

	/**
	 * 멤버를 제거한다. 역할 변경과 같은 이유로 마지막 OWNER는 제거할 수 없고, 같은 경쟁 조건을 행 잠금으로 막는다.
	 */
	@Transactional
	public void removeMember(Long actorId, Long projectId, Long memberId) {
		projectAccess.requireRole(actorId, projectId, ProjectRole.OWNER);
		ProjectMember member = members.lockByProjectAndUser(projectId, memberId)
				.orElseThrow(() -> new IllegalArgumentException("멤버를 찾을 수 없습니다."));
		if (member.getRole() == ProjectRole.OWNER
				&& members.countByIdProjectIdAndRole(projectId, ProjectRole.OWNER) == 1)
			throw new IllegalStateException("마지막 OWNER는 제거할 수 없습니다.");
		members.delete(member);
	}

	/**
	 * 비회원으로 만든 링크를 프로젝트로 옮긴다. 소유권을 넘기는 처리라 두 자격을 함께 요구한다.
	 * 프로젝트에 대한 EDITOR 권한과, 그 링크의 secret key다.
	 *
	 * <p>링크를 행 잠금으로 읽어 두 요청이 같은 링크를 동시에 가져가지 못하게 한다. 이미 프로젝트에 속했거나
	 * secret key가 맞지 않으면 링크 없음과 같은 문구로 거부해, 어떤 코드가 실재하는지 알려주지 않는다.</p>
	 *
	 * <p>편입 후 코드가 프로젝트 서브도메인 공간에서 충돌할 수 있어 flush 실패를 별도 예외로 바꾼다.</p>
	 */
	@Transactional
	public void importAnonymousLink(Long userId, Long projectId, String code, String secret) {
		projectAccess.requireRole(userId, projectId, ProjectRole.EDITOR);
		Link link = links.lockAnonymousByCode(code).orElseThrow(() -> new IllegalArgumentException("링크를 찾을 수 없습니다."));
		if (link.getProject() != null || link.getSecretKeyHash() == null
				|| !secretKeys.matches(secret, link.getSecretKeyHash()))
			throw new IllegalArgumentException("링크를 찾을 수 없습니다.");
		link.assignToProject(project(projectId), user(userId));
		try {
			links.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new LinkCodeConflictException();
		}
	}

	/**
	 * 웹에서 프로젝트 링크를 만든다. EDITOR 이상이어야 하며, 생성자는 확인된 멤버십의 사용자로 기록된다.
	 */
	public Link createProjectLink(Long userId, Long projectId, CreateLinkRequest request) {
		ProjectMember membership = projectAccess.requireRole(userId, projectId, ProjectRole.EDITOR);
		return linkManagement.createForProject(request, membership.getProject(), membership.getUser());
	}

	/**
	 * API key로 프로젝트 링크를 만든다. 위 웹 경로와 짝을 이루지만 인가 방식이 다르다.
	 * 키가 이 프로젝트의 것인지와 scope 확인은 컨트롤러가 이미 끝냈다고 보고 여기서는 다시 검사하지 않으므로,
	 * 확인 없이 이 메서드를 부르면 다른 프로젝트에 링크가 생긴다.
	 *
	 * <p>쓰기 레이트리밋을 여기서 거는 것은 이 경로가 자동화된 대량 호출의 대상이기 때문이다.</p>
	 *
	 * <p>멱등 키가 있으면 요청 내용의 해시를 함께 저장한다. 같은 키로 다른 내용을 보내면 충돌로 거부하기 위한 지문이다.</p>
	 */
	public Link createProjectLink(Long apiKeyId, Long projectId, String idempotencyKey, CreateLinkRequest request) {
		rateLimitService.checkApiKeyWrite(apiKeyId);
		String normalizedKey = validIdempotencyKey(idempotencyKey);
		String requestHash = normalizedKey == null ? null
				: InvitationTokenHash.sha256(
						String.valueOf(request.originalUrl()) + "\n" + String.valueOf(request.expiresAt()) + "\n"
								+ request.normalizedName());
		return linkManagement.createForProject(request, project(projectId), null,
				normalizedKey == null ? null : apiKeyId, normalizedKey, requestHash);
	}

	@Transactional
	public void deleteProjectLink(Long userId, Long projectId, String code) {
		projectAccess.requireRole(userId, projectId, ProjectRole.EDITOR);
		links.delete(projectLink(projectId, code));
	}

	/**
	 * 프로젝트 링크 상세를 돌려준다. VIEWER도 볼 수 있지만 수정은 못 하므로,
	 * 역할에 따라 편집 가능 여부를 응답에 담아 화면이 버튼 표시를 결정하게 한다.
	 * 이 값은 표시용일 뿐이며 실제 차단은 수정 경로의 권한 검사가 담당한다.
	 */
	@Transactional(readOnly = true)
	public LinkManagementResponse projectLink(Long userId, Long projectId, String code) {
		ProjectMember member = projectAccess.requireRole(userId, projectId, ProjectRole.VIEWER);
		return linkManagement.projectManagementResponse(projectLink(projectId, code),
				member.getRole() != ProjectRole.VIEWER);
	}

	@Transactional
	public LinkManagementResponse updateProjectLink(Long userId, Long projectId, String code,
			UpdateLinkRequest request) {
		projectAccess.requireRole(userId, projectId, ProjectRole.EDITOR);
		return linkManagement.updateProjectLink(projectLink(projectId, code), request);
	}

	// 삭제된 링크는 @SoftDelete가 조회에서 걸러내므로 여기서는 존재 여부만 본다.
	private Link projectLink(Long projectId, String code) {
		return links.findByProjectIdAndCode(projectId, code).orElseThrow(LinkNotFoundException::new);
	}

	private Project project(Long id) {
		return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
	}

	private User user(Long id) {
		return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	private String validName(String value) {
		if (value == null || value.trim().isEmpty() || value.trim().length() > 100)
			throw new IllegalArgumentException("프로젝트 이름은 1~100자로 입력하세요.");
		return value.trim();
	}

	/**
	 * 멱등 키의 문자와 길이를 제한한다. 이 값이 조회 키로 쓰이므로 임의 문자열을 그대로 받지 않는다.
	 * 값을 보내지 않은 경우는 멱등 처리를 하지 않겠다는 뜻이라 그대로 통과시킨다.
	 */
	private String validIdempotencyKey(String value) {
		if (value == null)
			return null;
		if (!value.matches("[A-Za-z0-9._:-]{1,100}"))
			throw new IllegalArgumentException("Idempotency-Key가 올바르지 않습니다.");
		return value;
	}

	private String optionalSubdomain(String requested) {
		if (requested == null || requested.isBlank())
			return null;
		String normalized = normalizedSubdomain(requested);
		if (projects.existsBySubdomain(normalized))
			throw new IllegalArgumentException("이미 사용 중인 서브도메인입니다.");
		return normalized;
	}

	/**
	 * 서브도메인 후보를 정규화하고 검증한다. 여기 규칙이 곧 호스트 이름의 제약이다.
	 * 소문자 영숫자와 하이픈만 허용하고 하이픈으로 시작하거나 끝날 수 없으며, 점이 없어 항상 한 단계다.
	 * 예약어는 {@code ProjectDomainService}가 판단한다.
	 */
	private String normalizedSubdomain(String requested) {
		if (requested == null)
			throw new IllegalArgumentException("서브도메인이 올바르지 않습니다.");
		String normalized = requested.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() < 3 || normalized.length() > 63 || !normalized.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])")
				|| domains.isReservedSubdomain(normalized))
			throw new IllegalArgumentException("서브도메인이 올바르지 않습니다.");
		return normalized;
	}

}

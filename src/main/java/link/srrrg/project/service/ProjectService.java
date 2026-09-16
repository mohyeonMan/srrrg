package link.srrrg.project.service;

import link.srrrg.project.membership.model.ProjectMember;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.project.membership.repository.ProjectMemberRepository;
import link.srrrg.project.membership.service.ProjectAccessService;
import link.srrrg.project.model.Project;
import link.srrrg.project.repository.ProjectRepository;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.utmtemplate.service.UtmTemplateService;
import link.srrrg.project.subdomain.service.ProjectSubdomainService;
import link.srrrg.identity.account.model.User;
import link.srrrg.identity.account.repository.UserRepository;

/**
 * 프로젝트 생성·조회·이름·서브도메인·삭제 유스케이스를 조율한다.
 *
 * <p>거의 모든 공개 메서드가 {@link ProjectAccessService#requireRole}로 시작한다. 프로젝트 자원은 멤버십이 있어야 접근할 수
 * 있고 역할에 따라 허용 범위가 다르므로, 조회 전에 권한을 확인하는 이 순서를 지켜야 한다.
 * 먼저 조회한 뒤 권한을 보면 존재 여부가 응답 차이로 새어 나간다.</p>
 *
 * <p>삭제된 프로젝트는 {@code @SoftDelete}와 조회 시 fetch join으로 걸러진다. 멤버십을 찾을 때
 * 프로젝트를 함께 조인하므로, 프로젝트가 삭제되면 멤버십 자체가 조회되지 않아 모든 접근이 권한 없음이 된다.</p>
 */
@Service
public class ProjectService {
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectAccessService projectAccessService;
	private final UserRepository users;
	private final ProjectSubdomainService subdomains;
	private final UtmTemplateService utmTemplateService;

	public ProjectService(ProjectRepository projects, ProjectMemberRepository members,
			ProjectAccessService projectAccessService, UserRepository users,
			ProjectSubdomainService subdomains, UtmTemplateService utmTemplateService) {
		this.projects = projects;
		this.members = members;
		this.projectAccessService = projectAccessService;
		this.users = users;
		this.subdomains = subdomains;
		this.utmTemplateService = utmTemplateService;
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
		utmTemplateService.createDefault(project);
		return project;
	}

	@Transactional(readOnly = true)
	public ProjectMember detail(Long userId, Long projectId) {
		return projectAccessService.requireRole(userId, projectId, ProjectRole.VIEWER);
	}

	@Transactional
	public Project rename(Long userId, Long projectId, String name) {
		Project project = projectAccessService.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
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
	/**
	 * 서브도메인 선점을 푼다. 그 호스트로 이미 발급된 링크는 접근 경로를 잃으므로,
	 * 되돌릴 수 없는 변경으로 보고 OWNER만 실행할 수 있다.
	 */
	/**
	 * 프로젝트를 soft delete한다. 링크와 멤버십을 지우지 않는 것이 중요하다.
	 * 조회 경로가 모두 프로젝트를 조인하므로, 프로젝트 한 행만 지워도 소속 자원 전체가 접근 불가가 된다.
	 * 통계 이벤트가 링크를 참조하고 있어 물리 삭제도 하지 않는다.
	 */
	@Transactional
	public void delete(Long userId, Long projectId) {
		projectAccessService.requireRole(userId, projectId, ProjectRole.OWNER);
		// @SoftDelete가 걸려 있어 bulk delete는 deleted_at을 찍는 UPDATE로 번역된다.
		projects.softDeleteById(projectId);
	}

	private User user(Long id) {
		return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	private String validName(String value) {
		if (value == null || value.trim().isEmpty() || value.trim().length() > 100)
			throw new IllegalArgumentException("프로젝트 이름은 1~100자로 입력하세요.");
		return value.trim();
	}

	private String optionalSubdomain(String requested) {
		if (requested == null || requested.isBlank())
			return null;
		return subdomains.available(requested);
	}

}

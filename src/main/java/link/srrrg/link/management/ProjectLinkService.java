package link.srrrg.link.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkCodeConflictException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.SecretKeyManager;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectAccessService;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectRepository;
import link.srrrg.project.ProjectRole;

/**
 * 프로젝트에 직접 소속된 링크의 조회·생성·수정·삭제와 익명 링크 편입을 조율한다.
 *
 * <p>웹 경로는 사용자 멤버십으로 권한을 확인한다. API key 경로는 컨트롤러가 키의 프로젝트와 scope를
 * 확인한 뒤 호출하므로, {@code ForApiKey} 메서드는 사용자 역할을 다시 조회하지 않는다.</p>
 */
@Service
public class ProjectLinkService {
	private final ProjectRepository projects;
	private final UserRepository users;
	private final LinkRepository links;
	private final SecretKeyManager secretKeyManager;
	private final LinkManagementService linkManagementService;
	private final ProjectAccessService projectAccessService;
	private final RateLimitService rateLimitService;

	public ProjectLinkService(ProjectRepository projects, UserRepository users, LinkRepository links,
			SecretKeyManager secretKeyManager, LinkManagementService linkManagementService,
			ProjectAccessService projectAccessService, RateLimitService rateLimitService) {
		this.projects = projects;
		this.users = users;
		this.links = links;
		this.secretKeyManager = secretKeyManager;
		this.linkManagementService = linkManagementService;
		this.projectAccessService = projectAccessService;
		this.rateLimitService = rateLimitService;
	}

	@Transactional(readOnly = true)
	public List<Link> listForWeb(Long userId, Long projectId) {
		projectAccessService.requireRole(userId, projectId, ProjectRole.VIEWER);
		return links.findByProjectIdAndCampaignIsNullOrderByIdDesc(projectId);
	}

	/**
	 * API key 목록 요청에서 커서 다음 항목을 최대 {@code limit + 1}개 읽는다. 다음 커서 계산과 응답 변환은
	 * HTTP 계약을 소유한 컨트롤러가 담당한다. 프로젝트와 scope 검사는 호출 전에 끝나 있어야 한다.
	 */
	@Transactional(readOnly = true)
	public List<Link> listForApiKey(Long projectId, Long cursor, int limit) {
		PageRequest page = PageRequest.of(0, limit + 1);
		return cursor == null
				? links.findByProjectIdAndCampaignIsNullOrderByIdDesc(projectId, page)
				: links.findByProjectIdAndCampaignIsNullAndIdLessThanOrderByIdDesc(projectId, cursor, page);
	}

	/**
	 * 비회원으로 만든 링크를 프로젝트로 옮긴다. 프로젝트 EDITOR 권한과 링크 secret key를 모두 요구하며,
	 * 편입에 성공하면 secret key가 폐기되어 이후 관리는 프로젝트 권한으로만 가능하다.
	 */
	@Transactional
	public void claimAnonymousForWeb(Long userId, Long projectId, String code, String secret) {
		projectAccessService.requireRole(userId, projectId, ProjectRole.EDITOR);
		Link link = links.lockAnonymousByCode(code)
				.orElseThrow(() -> new IllegalArgumentException("링크를 찾을 수 없습니다."));
		if (link.getProject() != null || link.getSecretKeyHash() == null
				|| !secretKeyManager.matches(secret, link.getSecretKeyHash()))
			throw new IllegalArgumentException("링크를 찾을 수 없습니다.");
		link.assignToProject(project(projectId), user(userId));
		try {
			links.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new LinkCodeConflictException();
		}
	}

	public Link createForWeb(Long userId, Long projectId, CreateLinkRequest request) {
		ProjectMember membership = projectAccessService.requireRole(userId, projectId, ProjectRole.EDITOR);
		return linkManagementService.createForProject(request, membership.getProject(), membership.getUser());
	}

	/**
	 * API key로 프로젝트 링크를 만든다. 쓰기 레이트리밋과 멱등 키 검증은 두 API 표면 중 자동화 호출을 받는
	 * 이 경로에만 적용한다. 프로젝트와 scope 검사는 호출 전에 끝나 있어야 한다.
	 */
	public Link createForApiKey(Long apiKeyId, Long projectId, String idempotencyKey, CreateLinkRequest request) {
		rateLimitService.checkApiKeyWrite(apiKeyId);
		String normalizedKey = validIdempotencyKey(idempotencyKey);
		String requestHash = normalizedKey == null ? null : requestFingerprint(request);
		return linkManagementService.createForProject(request, project(projectId), null,
				normalizedKey == null ? null : apiKeyId, normalizedKey, requestHash);
	}

	@Transactional
	public void deleteForWeb(Long userId, Long projectId, String code) {
		projectAccessService.requireRole(userId, projectId, ProjectRole.EDITOR);
		links.delete(projectLink(projectId, code));
	}

	/**
	 * VIEWER도 링크 설정을 볼 수 있으므로 역할에 따라 화면 표시용 편집 가능 여부를 함께 계산한다.
	 * 실제 수정 차단은 {@link #updateForWeb}의 권한 검사가 담당한다.
	 */
	@Transactional(readOnly = true)
	public LinkManagementResponse detailForWeb(Long userId, Long projectId, String code) {
		ProjectMember member = projectAccessService.requireRole(userId, projectId, ProjectRole.VIEWER);
		return linkManagementService.projectManagementResponse(projectLink(projectId, code),
				member.getRole() != ProjectRole.VIEWER);
	}

	@Transactional
	public LinkManagementResponse updateForWeb(Long userId, Long projectId, String code, UpdateLinkRequest request) {
		projectAccessService.requireRole(userId, projectId, ProjectRole.EDITOR);
		return linkManagementService.updateProjectLink(projectLink(projectId, code), request);
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

	private String validIdempotencyKey(String value) {
		if (value == null)
			return null;
		if (!value.matches("[A-Za-z0-9._:-]{1,100}"))
			throw new IllegalArgumentException("Idempotency-Key가 올바르지 않습니다.");
		return value;
	}

	/**
	 * 같은 멱등 키에 다른 요청이 들어왔는지 비교할 고정 길이 지문을 만든다.
	 */
	private String requestFingerprint(CreateLinkRequest request) {
		String payload = String.valueOf(request.originalUrl()) + "\n" + String.valueOf(request.expiresAt()) + "\n"
				+ request.normalizedName();
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

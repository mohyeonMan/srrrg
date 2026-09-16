package link.srrrg.project.subdomain.service;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.project.membership.service.ProjectAccessService;
import link.srrrg.project.model.Project;
import link.srrrg.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;

/** 프로젝트의 서브도메인 선점, 활성화와 해제 정책을 담당한다. */
@Service
@RequiredArgsConstructor
public class ProjectSubdomainService {
	private final ProjectRepository projects;
	private final ProjectAccessService projectAccess;
	private final ProjectSubdomainPolicy policy;

	@Transactional(readOnly = true)
	public Project get(Long userId, Long projectId) {
		return projectAccess.requireRole(userId, projectId, ProjectRole.VIEWER).getProject();
	}

	@Transactional
	public Project claim(Long userId, Long projectId, String requested) {
		Project project = projectAccess.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		String subdomain = normalize(requested);
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
	public Project setEnabled(Long userId, Long projectId, boolean enabled) {
		Project project = projectAccess.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		project.setSubdomainEnabled(enabled);
		return project;
	}

	@Transactional
	public Project release(Long userId, Long projectId) {
		Project project = projectAccess.requireRole(userId, projectId, ProjectRole.OWNER).getProject();
		project.releaseSubdomain();
		return project;
	}

	/** 프로젝트 생성에서도 같은 예약어와 중복 규칙을 사용하도록 검증된 값을 반환한다. */
	public String available(String requested) {
		String normalized = normalize(requested);
		if (projects.existsBySubdomain(normalized)) throw new IllegalArgumentException("이미 사용 중인 서브도메인입니다.");
		return normalized;
	}

	private String normalize(String requested) {
		if (requested == null) throw new IllegalArgumentException("서브도메인이 올바르지 않습니다.");
		String normalized = requested.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() < 3 || normalized.length() > 63
				|| !normalized.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])")
				|| policy.isReserved(normalized)) {
			throw new IllegalArgumentException("서브도메인이 올바르지 않습니다.");
		}
		return normalized;
	}
}

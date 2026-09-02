package link.srrrg.project;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 멤버십의 조회와 역할 변경, 제거를 담당한다.
 *
 * <p>프로젝트에 속한 사람과 역할은 프로젝트 자원의 접근 권한을 결정하므로, 공개 메서드는
 * {@link ProjectAccessService}를 통해 조회 또는 변경에 필요한 최소 역할을 먼저 확인한다.</p>
 */
@Service
public class ProjectMemberService {
	private final ProjectMemberRepository members;
	private final ProjectAccessService projectAccess;

	public ProjectMemberService(ProjectMemberRepository members, ProjectAccessService projectAccess) {
		this.members = members;
		this.projectAccess = projectAccess;
	}

	@Transactional(readOnly = true)
	public List<ProjectMember> myMemberships(Long userId) {
		return members.findActiveByUserId(userId);
	}

	@Transactional(readOnly = true)
	public List<ProjectMember> projectMembers(Long userId, Long projectId) {
		projectAccess.requireRole(userId, projectId, ProjectRole.VIEWER);
		return members.findByIdProjectId(projectId);
	}

	/**
	 * 멤버 역할을 바꾼다. 마지막 OWNER를 강등하면 프로젝트 관리와 삭제가 불가능해지므로 거부한다.
	 * 대상 행을 잠가 같은 멤버에 대한 변경은 직렬화하지만, 서로 다른 OWNER를 동시에 바꾸는 경쟁 조건은
	 * 별도 프로젝트 단위 잠금 없이는 완전히 막지 못한다.
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
	 * 멤버를 제거한다. 역할 변경과 마찬가지로 마지막 OWNER는 제거할 수 없다.
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
}

package link.srrrg.project;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자에게 프로젝트 역할이 있는지 판정하는 단일 관문이다. 프로젝트를 소유한 기능이 이 정책을 맡고,
 * 캠페인·링크·통계는 멤버십 저장소를 직접 조회하지 않는다.
 *
 * <p>멤버십 조회가 프로젝트를 함께 조인하므로 삭제된 프로젝트의 멤버십도 접근 불가로 처리된다.
 * 반환한 멤버십은 권한을 통과한 프로젝트와 사용자가 필요한 후속 유스케이스에서 재사용한다.</p>
 */
@Service
public class ProjectAccessService {
	private final ProjectMemberRepository members;

	public ProjectAccessService(ProjectMemberRepository members) {
		this.members = members;
	}

	/**
	 * 요청한 역할 이상의 활성 멤버십을 반환한다. {@link ProjectRole} 선언 순서가 앞일수록 권한이 크다.
	 *
	 * @throws SecurityException 멤버십이 없거나 역할이 부족한 경우
	 */
	@Transactional(readOnly = true)
	public ProjectMember requireRole(Long userId, Long projectId, ProjectRole minimum) {
		ProjectMember membership = members.findActiveByProjectAndUser(projectId, userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
		if (membership.getRole().ordinal() > minimum.ordinal()) {
			throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		}
		return membership;
	}
}

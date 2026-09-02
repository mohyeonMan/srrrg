package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ProjectMemberServiceTest {
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectMemberService service = new ProjectMemberService(members, new ProjectAccessService(members));

	@Test
	void blocksDemotionOfLastOwner() {
		ProjectMember owner = mock(ProjectMember.class);
		Project project = mock(Project.class);
		when(owner.getRole()).thenReturn(ProjectRole.OWNER);
		when(owner.getProject()).thenReturn(project);
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(owner));
		when(members.lockByProjectAndUser(1L, 3L)).thenReturn(Optional.of(owner));
		when(members.countByIdProjectIdAndRole(1L, ProjectRole.OWNER)).thenReturn(1L);

		assertThatThrownBy(() -> service.changeMemberRole(2L, 1L, 3L, ProjectRole.EDITOR))
				.isInstanceOf(IllegalStateException.class);
		verify(owner, never()).changeRole(any());
	}
}

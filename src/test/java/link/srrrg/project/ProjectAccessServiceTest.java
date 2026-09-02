package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ProjectAccessServiceTest {
	private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
	private final ProjectAccessService service = new ProjectAccessService(members);

	@Test
	void returnsMembershipWhenRoleIsSufficient() {
		ProjectMember editor = mock(ProjectMember.class);
		when(editor.getRole()).thenReturn(ProjectRole.EDITOR);
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(editor));

		assertThat(service.requireRole(2L, 1L, ProjectRole.EDITOR)).isSameAs(editor);
	}

	@Test
	void rejectsMemberWhoseRoleIsInsufficient() {
		ProjectMember viewer = mock(ProjectMember.class);
		when(viewer.getRole()).thenReturn(ProjectRole.VIEWER);
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.of(viewer));

		assertThatThrownBy(() -> service.requireRole(2L, 1L, ProjectRole.EDITOR))
				.isInstanceOf(SecurityException.class)
				.hasMessage("프로젝트 접근 권한이 없습니다.");
	}

	@Test
	void rejectsUserWithoutActiveMembership() {
		when(members.findActiveByProjectAndUser(1L, 2L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.requireRole(2L, 1L, ProjectRole.VIEWER))
				.isInstanceOf(SecurityException.class)
				.hasMessage("프로젝트 접근 권한이 없습니다.");
	}
}

package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProjectMembersPageAssetTest {

	@Test
	void providesInvitationManagementAndRendersUserValuesAsText() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/project-members.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-project-members.js"));
		String projectsScript = Files.readString(Path.of("src/main/resources/static/js/srrrg-projects.js"));

		assertThat(template).contains(
				"id=\"project-members-app\"",
				"id=\"invite-form\"",
				"id=\"invite-message\"",
				"id=\"invitation-count\"",
				"aria-live=\"polite\"",
				"링크 편집 가능");
		assertThat(script).contains(
				"new URLSearchParams(location.search).get('projectId')",
				"/api/web/projects/${projectId}/members",
				"/api/web/projects/${projectId}/invitations",
				"window.SrrrgProjectMembers = { reload: load }",
				"roleLabel(invitation.role)",
				// 공통 fetch/DOM 헬퍼는 srrrg-common.js 로 옮겨져 여기서는 가져다 쓴다.
				"= SrrrgCommon",
				"replaceChildren(byId(");
		assertThat(script).doesNotContain("innerHTML", "localStorage", "sessionStorage");
		// 멤버는 기본 탭이 아니므로 조각이 스스로 부르지 않는다. 그러면 주소에 projectId 가
		// 없는(자동 선택) 경우 어떤 프로젝트인지 알 수 없으므로, 탭을 처음 열 때
		// srrrg-projects.js 가 프로젝트를 알려 주어야 한다.
		assertThat(projectsScript).contains("window.SrrrgProjectMembers", "target.reload(String(state.selected.id))");
		assertThat(script).doesNotContain("if (projectId) load();");
	}
}

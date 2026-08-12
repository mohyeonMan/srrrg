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
		assertThat(projectsScript).contains("window.SrrrgProjectMembers?.reload(String(project.id))");
	}
}

package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InvitationPageAssetTest {

	@Test
	void providesAccessibleInvitationAcceptanceWithoutBrowserTokenStorage() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/invitation.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-invitation.js"));

		assertThat(template).contains(
				"data-available=${invitation.available}",
				"id=\"accept-invitation\"",
				"role=\"status\"",
				"aria-live=\"polite\"",
				"초대받은 이메일과 계정 이메일을 비교하지 않습니다");
		assertThat(script).contains(
				"/api/web/invitations/${encodeURIComponent(token)}/accept",
				"/api/web/auth/refresh",
				"/projects?projectId=${encodeURIComponent(body.projectId)}");
		assertThat(script).doesNotContain("innerHTML", "localStorage", "sessionStorage");
	}
}

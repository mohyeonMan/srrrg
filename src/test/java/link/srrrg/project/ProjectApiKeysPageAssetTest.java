package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProjectApiKeysPageAssetTest {

	@Test
	void managesProjectApiKeysWithoutPersistingTheOneTimeSecret() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/project-api-keys.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-project-api-keys.js"));
		String styles = Files.readString(Path.of("src/main/resources/static/css/srrrg/projects.css"));

		assertThat(template).contains(
				"id=\"project-api-keys-app\"",
				"id=\"api-key-scroll\"",
				"id=\"api-key-active-list\"",
				"id=\"api-key-history\"",
				"id=\"create-api-key-dialog\"",
				"name=\"scope\" value=\"links:read\"",
				"name=\"scope\" value=\"campaigns:write\"",
				"type=\"datetime-local\"",
				"id=\"created-api-key\"",
				"이 키는 지금 한 번만 확인할 수 있습니다");
		assertThat(script).contains(
				"/api/web/projects/${projectId}/api-keys",
				"method: 'POST'",
				"method: 'DELETE'",
				"item.setAttribute('name', 'api-key-detail')",
				"navigator.clipboard.writeText",
				"byId('created-api-key').textContent = ''",
				"window.SrrrgProjectApiKeys");
		assertThat(script).doesNotContain("innerHTML", "localStorage", "sessionStorage");
		assertThat(styles).contains(
				".api-key-scroll",
				"max-height: min(480px, 55dvh)",
				"overflow-y: auto",
				"scrollbar-width: thin",
				".api-key-item > summary:hover",
				".api-key-metadata");
	}
}

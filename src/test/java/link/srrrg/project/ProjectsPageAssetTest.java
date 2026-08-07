package link.srrrg.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProjectsPageAssetTest {

	@Test
	void providesProjectLinkCreationAndRendersUserValuesAsText() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/projects.html"));
		String layout = Files.readString(Path.of("src/main/resources/templates/fragments/srrrg-layout.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-projects.js"));

		assertThat(template).contains(
				"id=\"create-project-link-form\"",
				"type=\"url\"",
				"data-expires-option=\"custom\"",
				"id=\"project-link-result\"",
				"aria-live=\"polite\"");
		assertThat(script).contains(
				"/api/web/projects/${state.selected.id}/links",
				"originalUrl: originalUrlInput.value.trim()",
				"expiresAt: expiresAt()",
				"navigator.clipboard.writeText",
				"replaceChildren(...children)");
		assertThat(script).doesNotContain("innerHTML", "localStorage", "sessionStorage");
		assertThat(layout).contains("th:href=\"@{/projects}\">프로젝트</a>");
	}
}

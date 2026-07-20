package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ManagementPageAssetTest {

	@Test
	void managementViewDoesNotExposeInternalUrlVerificationCache() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/fragments/management-modal.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-home.js"));

		assertThat(template).doesNotContain("management-verification-status", "management-verified-at");
		assertThat(template).contains("사용 가능");
		assertThat(script).doesNotContain("NO_THREAT_FOUND", "THREAT_DETECTED", "CHECK_FAILED", "verifiedAt");
		assertThat(script).contains("expired ? '만료됨' : '사용 가능'");
	}
}

package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ManagementPageAssetTest {

	@Test
	void managementConsoleProtectsCredentialsAndDoesNotExposeInternalVerificationCache() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/management.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-management.js"));

		assertThat(template).doesNotContain("management-verification-status", "management-verified-at");
		assertThat(template).contains("사용 가능", "type=\"password\"").doesNotContain("샘플 통계");
		assertThat(script).doesNotContain("NO_THREAT_FOUND", "THREAT_DETECTED", "verifiedAt", "buildSampleAnalytics");
		assertThat(script).doesNotContain("localStorage", "sessionStorage");
		assertThat(script).contains("expired ? '만료됨' : '사용 가능'", "X-Srrrg-Secret-Key", "/statistics?", "projectMode", "X-XSRF-TOKEN");
		assertThat(template).contains("metric-human-entries", "metric-human-redirects", "metric-human-rate");
		assertThat(script).contains("analytics.summary.current", "formatComparison", "신규 유입");
	}
}

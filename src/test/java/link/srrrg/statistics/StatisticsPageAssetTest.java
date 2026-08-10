package link.srrrg.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StatisticsPageAssetTest {
	@Test
	void usesPeriodMetricsTypedUtmAndExplicitPagination() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/statistics.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-statistics.js"));

		assertThat(template).contains("사람 진입", "사람 이동", "전체 기간 상태",
				"id=\"load-more-statistics-links\"", "id=\"load-more-statistics-utm\"");
		assertThat(script).contains("data.summary.current", "data.summary.previous",
				"item.period.humanEntries", "item.redirectedEvents", "page.nextOffset",
				"신규 유입", "requestSequence");
		assertThat(script).doesNotContain("data.summary.entries", "item.entries}</td><td>${item.redirects");
	}
}

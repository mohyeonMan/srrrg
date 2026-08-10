package link.srrrg.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CampaignsPageAssetTest {

	@Test
	void previewsCurrentEffectiveUtmAndShowsItsSourceForEachLink() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/campaigns.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-campaigns.js"));

		assertThat(template).contains(
				"id=\"campaign-utm-preview\"",
				"id=\"campaign-utm-preview-list\"",
				"id=\"campaign-link-select-all\"",
				"id=\"delete-selected-links-button\"",
				"class=\"campaign-link-table\"",
				"현재 입력 기준 적용 예정 UTM",
				"기존 링크의 리다이렉트에도 즉시 적용");
		assertThat(script).contains(
				"state.utmDefaults[field.name]",
				"items.flatMap(campaignLinkRows)",
				"state.selectedLinkCodes",
				"method: 'DELETE', body: JSON.stringify({ codes })",
				"effectiveUtmPanel(values)",
				"value.source === 'INPUT' ? '개별 입력'",
				"value.source === 'LINK' ? '링크 개별값' : '캠페인 기본값'",
				"링크에서 직접 지정하지 않은 필드에 즉시 적용됩니다.");
		assertThat(script).doesNotContain("innerHTML");
	}
}

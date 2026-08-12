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
				"id=\"campaign-link-select-all\"",
				"id=\"delete-selected-links-button\"",
				// 좁은 폭에서 가로 스크롤 대신 카드로 접히도록 공용 .stacking-table 을 함께 쓴다.
				"class=\"campaign-link-table stacking-table\"",
				"id=\"manage-utm-templates-link\"",
				"템플릿 구조 변경은 UTM 템플릿 관리에서만");
		// 적용 예정 UTM 은 템플릿 인라인 섹션이 아니라 링크별 팝오버로 렌더링한다.
		assertThat(script).contains(
				"state.utmDefaults[field.name]",
				"items.map(campaignLinkRows)",
				"state.selectedLinkCodes",
				"method: 'DELETE', body: JSON.stringify({ codes })",
				"function showUtmPopover(anchor, values)",
				"현재 적용 예정 UTM",
				"value.source === 'INPUT' ? '개별 입력'",
				"value.source === 'LINK' ? '링크 개별값' : '캠페인 기본값'",
				"링크에서 직접 지정하지 않은 필드에 즉시 적용됩니다.");
		assertThat(template).doesNotContain("id=\"create-template-form\"", "id=\"add-field-form\"", "id=\"reload-templates-button\"");
		assertThat(script).doesNotContain("deleteField(", "템플릿을 만들었습니다", "/projects/utm-templates");
		assertThat(script).doesNotContain("innerHTML");
	}
}

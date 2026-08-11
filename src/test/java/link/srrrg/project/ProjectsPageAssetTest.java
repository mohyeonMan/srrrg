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
		String styles = Files.readString(Path.of("src/main/resources/static/css/srrrg/projects.css"));

		assertThat(template).contains(
				"~{fragments/srrrg-layout :: header}",
				"id=\"project-heading-name\"",
				"id=\"project-picker\"",
				"id=\"open-create-project\"",
				"id=\"create-project-dialog\"",
				"id=\"project-activity-list\"",
				"id=\"project-activity-scrollbar\"",
				"id=\"project-activity-scroll-thumb\"",
				"id=\"project-create-link-nav\"",
				"id=\"project-create-campaign-nav\"",
				"id=\"project-templates-nav\"",
				"id=\"project-settings-nav\"",
				"id=\"project-activity-filter\"",
				"id=\"project-activity-sort\"",
				"id=\"project-campaign-view\"",
				"id=\"project-link-view\"",
				"id=\"project-template-view\"",
				"id=\"project-settings-view\"",
				"~{campaigns :: workspace}",
				"~{management :: workspace}",
				"~{project-utm-templates :: workspace}",
				"~{project-settings :: workspace}",
				"id=\"project-home-panel\"",
				"id=\"create-project-link-form\"",
				"id=\"project-link-name\"",
				"type=\"url\"",
				"data-expires-option=\"custom\"",
				"id=\"project-link-result\"",
				"aria-live=\"polite\"");
		assertThat(script).contains(
				"/api/web/projects/${state.selected.id}/links",
				"originalUrl: originalUrlInput.value.trim()",
				"item.value.name || '이름없음'",
				"expiresAt: expiresAt()",
				"new URLSearchParams(location.search).get('projectId')",
				"renderProjectItems(links, campaigns)",
				"/projects?projectId=${state.selected.id}&campaignId=${item.value.id}",
				"/projects?projectId=${state.selected.id}&linkCode=${encodeURIComponent(item.value.code)}",
				".sort((left, right)",
				"state.activitySort === 'oldest'",
				"state.activityFilter === 'all'",
				"localeCompare",
				"navigator.clipboard.writeText",
				// 공통 fetch/DOM 헬퍼는 srrrg-common.js 로 옮겨져 여기서는 가져다 쓴다.
				"= SrrrgCommon",
				"replaceChildren(");
		assertThat(template).doesNotContain("workspace-sidebar", "workspace-shell :: sidebar");
		assertThat(script).doesNotContain("innerHTML", "localStorage", "sessionStorage");
		assertThat(script).doesNotContain("location.hash", "scrollIntoView");
		assertThat(styles).contains(
				"height: calc(100vh - 40px)",
				"align-self: start",
				".project-activity-list",
				"align-content: start",
				"scrollbar-width: none",
				"height: 36px",
				// 레일 반응형 3단: 데스크톱 전체 / 태블릿 아이콘 스트립 / 모바일 드로어
				"@media (max-width: 1023px)",
				"@media (max-width: 767px)",
				"grid-template-columns: 64px minmax(0, 1fr)",
				".rail-scrim");
		// 워크스페이스의 암시적 auto 트랙이 자식 min-content 로 늘어나 태블릿 폭에서
		// 문서가 가로 스크롤되던 문제를 막는 클램프.
		assertThat(styles).contains("grid-template-columns: minmax(0, 1fr)");
		// 조각들이 페이지 껍데기를 들고 오지 않으므로 뷰별 억제 규칙도 없어야 한다.
		assertThat(styles).doesNotContain(
				"min-height: 120dvh",
				".project-template-view #utm-templates-app > .projects-heading",
				"#back-to-project");
		assertThat(layout).contains("th:href=\"@{/projects}\">프로젝트</a>");
	}
}

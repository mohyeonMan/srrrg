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
				// 생성은 헤딩 CTA + 대화상자다. 레일에는 섹션 shortcut 둘만 남는다 —
				// 넷 다 .project-activity-item 이던 동안 동작과 캠페인 객체가 같은 옷이었다.
				"id=\"project-create-actions\"",
				"id=\"open-create-link\"",
				"id=\"open-create-campaign\"",
				"id=\"create-link-dialog\"",
				"id=\"create-campaign-dialog\"",
				"class=\"project-rail-shortcut\"",
				"id=\"project-templates-nav\"",
				"id=\"project-settings-nav\"",
				"id=\"project-activity-filter\"",
				"id=\"project-activity-sort\"",
				"id=\"project-campaign-view\"",
				"id=\"project-link-view\"",
				// UTM·설정은 별도 뷰가 아니라 프로젝트 탭 패널이다. 네 탭이 한 축(?tab=)에 있어야
				// 프로젝트가 캠페인·링크 레벨과 같은 문법을 갖는다.
				"id=\"project-tab-overview\"",
				"id=\"project-tab-members\"",
				"id=\"project-tab-utm\"",
				"id=\"project-tab-settings\"",
				"id=\"project-panel-overview\"",
				"id=\"project-panel-members\"",
				"id=\"project-panel-utm\"",
				"id=\"project-panel-settings\"",
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
				"replaceChildren(",
				// 프로젝트의 네 면이 한 축(?tab=)에 있어야 캠페인·링크 레벨과 문법이 같다.
				"const PROJECT_TABS = ['overview', 'members', 'utm', 'settings']",
				// 레일 shortcut 은 별도 뷰가 아니라 해당 탭으로 간다.
				"&tab=utm",
				"&tab=settings",
				// 예전 주소로 저장된 링크·북마크가 빈 개요로 떨어지지 않아야 한다.
				"LEGACY_VIEW_TABS",
				// 조각이 상시 렌더되므로 패널 데이터는 그 탭을 처음 볼 때만 불러온다.
				"function loadTabOnce",
				// shortcut 활성 표시는 탭이 바뀔 때마다 다시 칠해야 중복으로 읽히지 않는다.
				"function paintRailActive");
		assertThat(template).doesNotContain("workspace-sidebar", "workspace-shell :: sidebar");
		// 생성이 다시 인라인 판으로 돌아가면 열 때마다 요약·통계·탭 바가 숨겨진다.
		assertThat(template).doesNotContain("id=\"link-create-panel\"", "id=\"campaign-create-panel\"");
		// 생성 대화상자는 #project-detail 바깥(페이지 수준)에 있어야 한다.
		// 안에 두면 캠페인·링크·설정 화면에서 #project-detail 이 hidden 이 되어
		// display:none 조상 아래의 <dialog> 가 되고, showModal() 해도 렌더되지 않는다.
		assertThat(template.indexOf("id=\"create-link-dialog\""))
				.isLessThan(template.indexOf("id=\"project-detail\""));
		assertThat(template.indexOf("id=\"create-campaign-dialog\""))
				.isLessThan(template.indexOf("id=\"project-detail\""));
		assertThat(script).doesNotContain("innerHTML", "localStorage", "sessionStorage");
		assertThat(script).doesNotContain("location.hash", "scrollIntoView");
		assertThat(styles).contains(
				"height: calc(100vh - 40px)",
				"align-self: start",
				".project-activity-list",
				"align-content: start",
				// 네이티브 스크롤바를 그대로 쓴다.
				"scrollbar-width: thin",
				// 레일 반응형 2단: 데스크톱 전체 노출 / 1024px 아래는 드로어.
				"@media (max-width: 1023px)",
				".rail-scrim");
		// 태블릿에서 필터·정렬을 숨기면 콘텐츠를 걸러낼 방법이 사라진다.
		assertThat(styles).doesNotContain(".project-activity-controls,\n\t.project-activity-scrollbar {\n\t\tdisplay: none;");
		// 태블릿 아이콘 스트립은 폐기했다. 콘텐츠 목록의 라벨을 clip 해서 캠페인이 여러 개면
		// 같은 글리프만 쌓였고, 구분 수단인 title 툴팁은 터치 기기에서 뜨지 않았다.
		assertThat(styles).doesNotContain("grid-template-columns: 88px minmax(0, 1fr)");
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

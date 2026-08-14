package link.srrrg.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UtmTemplatesPageAssetTest {

	@Test
	void managesTemplateItselfNotOnlyItsFields() throws IOException {
		String template = Files.readString(Path.of("src/main/resources/templates/project-utm-templates.html"));
		String script = Files.readString(Path.of("src/main/resources/static/js/srrrg-project-utm-templates.js"));

		// 오랫동안 필드만 더하고 지울 수 있어서, 한 번 만든 템플릿은 이름도 못 바꾸고 지울 수도 없었다.
		// 서버에는 PATCH/DELETE 가 이미 있었으므로 화면만의 공백이었다.
		assertThat(template).contains(
				"id=\"rename-template\"",
				"id=\"delete-template\"",
				"id=\"rename-template-form\"",
				"id=\"cancel-rename-template\"");
		assertThat(script).contains(
				"method: 'PATCH'",
				"method: 'DELETE'",
				"/utm-templates/${templateId}",
				// 되돌리기 어려운 동작은 공용 확인 대화상자를 거친다.
				"confirmAction(");

		// 사용 중인 캠페인이 있으면 서버가 거부한다. 그 문구를 삼키지 말고 그대로 보여 줘야
		// 무엇을 먼저 해야 하는지 알 수 있다("먼저 캠페인의 템플릿을 변경하세요").
		assertThat(script).contains("(await body(response)).message || '템플릿을 삭제할 수 없습니다.'");

		// 이름 입력칸을 항상 열어 두면 제목과 같은 값이 두 번 보인다. 누를 때만 연다.
		assertThat(template).contains("id=\"rename-template-form\" class=\"project-inline-form\" novalidate hidden");

		// 조각이 스스로 초기 조회를 하면 UTM 탭을 보지 않는 사람도 매번 목록을 부른다.
		assertThat(script).contains("window.SrrrgProjectUtmTemplates");
		assertThat(script).doesNotContain("innerHTML", "localStorage", "sessionStorage");
	}
}

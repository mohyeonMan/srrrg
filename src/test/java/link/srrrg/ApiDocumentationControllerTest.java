package link.srrrg;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import link.srrrg.common.config.OpenApiConfig;

class ApiDocumentationControllerTest {
	@Test
	void exposesStablePublicOpenApiAndBrandedDocumentation() throws Exception {
		ApiDocumentationController controller = new ApiDocumentationController();
		String page = new String(new ClassPathResource("templates/api-docs.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(controller.documentation()).isEqualTo("api-docs");
		assertThat(controller.openApi()).isEqualTo("forward:/v3/api-docs/public");
		assertThat(page).contains("API 문서", "/openapi.json", "Authorization: Bearer", "context-path",
				"인증과 권한", "공통 규칙", "엔드포인트", "요청 한도",
				"links:read", "campaigns:write", "stats:read",
				"/api/v1/projects/{projectId}/links", "/api/v1/campaigns/{campaignId}/links/batch")
				.doesNotContain("srrrg-api-docs.js", "api-contract", "JSON.stringify");
		assertThat(new OpenApiConfig().publicOpenApi()).isNotNull();
	}

	@Test
	void disablesSwaggerUiByDefaultAndKeepsPublicLinksOffTheSwaggerPath() throws Exception {
		String config = new String(new ClassPathResource("application.yaml").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String layout = new String(new ClassPathResource("templates/fragments/srrrg-layout.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String home = new String(new ClassPathResource("templates/index.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(config).contains("SRRRG_SWAGGER_UI_ENABLED:false");
		assertThat(layout).contains("@{/docs/api}", ">API</a>").doesNotContain("swagger-ui");
		assertThat(home).doesNotContain("home/api-links", "OpenAPI JSON");
	}
}

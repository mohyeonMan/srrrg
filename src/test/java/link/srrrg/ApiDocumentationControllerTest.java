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
		String script = new String(new ClassPathResource("static/js/srrrg-api-docs.js").getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(controller.documentation()).isEqualTo("api-docs");
		assertThat(controller.openApi()).isEqualTo("forward:/v3/api-docs/public");
		assertThat(page).contains("srrrg API", "/openapi.json", "Authorization: Bearer", "srrrg-api-docs.js", "context-path");
		assertThat(script).contains("`${base}/openapi.json`").doesNotContain("fetch(\"/openapi.json\")");
		assertThat(new OpenApiConfig().publicOpenApi()).isNotNull();
	}

	@Test
	void disablesSwaggerUiByDefaultAndKeepsPublicLinksOffTheSwaggerPath() throws Exception {
		String config = new String(new ClassPathResource("application.yaml").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String homeLinks = new String(new ClassPathResource("templates/fragments/home/api-links.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8);

		assertThat(config).contains("SRRRG_SWAGGER_UI_ENABLED:false");
		assertThat(homeLinks).contains("@{/docs/api}", "@{/openapi.json}").doesNotContain("swagger-ui");
	}
}

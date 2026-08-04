package link.srrrg.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI srrrgOpenApi() {
		return new OpenAPI().info(new Info()
				.title("srrrg API")
				.version("v1")
				.description("srrrg 단축 URL API"))
				.components(new Components().addSecuritySchemes("projectApiKey", new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("API key")));
	}

	@Bean
	public GroupedOpenApi publicOpenApi() {
		return GroupedOpenApi.builder().group("public").pathsToMatch("/api/links/**", "/api/v1/**").build();
	}
}

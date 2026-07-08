package link.srrrg.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI srrrgOpenApi() {
		return new OpenAPI().info(new Info()
				.title("srrrg API")
				.version("v1")
				.description("비회원 기반 단축 URL 생성 및 리다이렉트 API"));
	}
}

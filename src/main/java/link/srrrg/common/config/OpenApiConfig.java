package link.srrrg.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
/**
 * springdoc이 만드는 API 문서의 범위와 인증 스킴을 정의한다.
 *
 * <p>{@code public} 그룹에 비인증 링크 API와 API 키 표면만 넣은 것은 의도된 선택이다.
 * 쿠키 JWT로만 접근할 수 있는 {@code /api/web/**}는 외부 연동 대상이 아니라 화면 전용 내부 API이므로
 * 문서에 노출하지 않는다. 새 외부 공개 엔드포인트를 만들면 아래 경로 목록에 추가해야 문서에 나온다.</p>
 */
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
	// bearer 스킴으로 선언하지만 실제 값은 srrrg_pk_로 시작하는 프로젝트 API 키다. JWT가 아니다.
	public GroupedOpenApi publicOpenApi() {
		return GroupedOpenApi.builder().group("public").pathsToMatch("/api/links/**", "/api/v1/**").build();
	}
}

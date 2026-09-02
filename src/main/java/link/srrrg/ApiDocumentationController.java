package link.srrrg;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * API 문서 화면과 OpenAPI 문서를 노출한다.
 *
 * <p>{@code /openapi.json}은 springdoc이 만드는 {@code public} 그룹 문서로 포워딩한다.
 * 외부에 안내하는 주소를 springdoc의 내부 경로와 분리해 두어, 문서 도구 설정이 바뀌어도
 * 공개 주소는 유지된다. 그룹 범위는 {@code OpenApiConfig}가 정한다.</p>
 */
@Controller
public class ApiDocumentationController {
	@GetMapping("/docs/api")
	public String documentation() { return "api-docs"; }
	@GetMapping("/openapi.json")
	public String openApi() { return "forward:/v3/api-docs/public"; }
}

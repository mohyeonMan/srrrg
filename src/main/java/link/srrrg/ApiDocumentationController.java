package link.srrrg;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ApiDocumentationController {
	@GetMapping("/docs/api")
	public String documentation() { return "api-docs"; }
	@GetMapping("/openapi.json")
	public String openApi() { return "forward:/v3/api-docs/public"; }
}

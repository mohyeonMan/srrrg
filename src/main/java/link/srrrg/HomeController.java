package link.srrrg;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "index";
	}

	@GetMapping("/manage")
	public String management(
			@RequestParam(required = false) String code,
			Model model
	) {
		model.addAttribute("prefilledCode", code == null ? "" : code);
		return "management";
	}

	@GetMapping("/projects")
	public String projects() {
		return "projects";
	}

	@GetMapping("/account")
	public String account() {
		return "account";
	}

	@GetMapping("/onboarding")
	public String onboarding() {
		return "onboarding";
	}

	// project-members / project-settings / project-utm-templates / campaigns / statistics 는
	// projects.html 안에서만 쓰이는 조각입니다. 단독 라우트를 두면 프로젝트 컨텍스트(레일·projectId)가
	// 없는 화면이 렌더링되므로 /projects?projectId=..&view=.. 로만 진입합니다.

	@GetMapping("/test/utm")
	public String utmTest() { return "utm-test"; }

	@GetMapping("/favicon.ico")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void favicon() {
	}
}

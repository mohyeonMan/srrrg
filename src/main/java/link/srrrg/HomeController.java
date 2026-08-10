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

	@GetMapping("/projects/members")
	public String projectMembers() {
		return "project-members";
	}

	@GetMapping("/projects/settings")
	public String projectSettings() {
		return "project-settings";
	}

	@GetMapping("/projects/utm-templates")
	public String projectUtmTemplates() {
		return "project-utm-templates";
	}

	@GetMapping("/campaigns")
	public String campaigns() {
		return "campaigns";
	}

	@GetMapping("/statistics")
	public String statistics() { return "statistics"; }

	@GetMapping("/test/utm")
	public String utmTest() { return "utm-test"; }

	@GetMapping("/favicon.ico")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void favicon() {
	}
}

package link.srrrg.auth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class LoginController {

	@GetMapping("/login")
	String login(@RequestParam(defaultValue = "/") String returnTo, Model model) {
		model.addAttribute("returnTo", returnTo);
		return "login";
	}
}

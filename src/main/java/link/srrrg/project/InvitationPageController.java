package link.srrrg.project;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class InvitationPageController {
	@GetMapping("/invitations/{token}")
	public String invitation(@PathVariable String token, Model model) {
		model.addAttribute("token", token);
		return "invitation";
	}
}

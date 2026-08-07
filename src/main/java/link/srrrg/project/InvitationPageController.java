package link.srrrg.project;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import link.srrrg.auth.SrrrgPrincipal;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class InvitationPageController {
	private final ProjectService projects;

	@GetMapping("/invitations/{token}")
	public String invitation(@PathVariable String token, @AuthenticationPrincipal SrrrgPrincipal principal, Model model) {
		ProjectService.InvitationPreview invitation = projects.invitationPreview(token);
		model.addAttribute("token", token);
		model.addAttribute("invitation", invitation);
		model.addAttribute("authenticated", principal != null);
		model.addAttribute("returnTo", "/invitations/" + token);
		return "invitation";
	}
}

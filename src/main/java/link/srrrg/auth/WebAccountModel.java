package link.srrrg.auth;

import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.identity.UserRepository;

@ControllerAdvice
class WebAccountModel {
	private final UserRepository users;

	WebAccountModel(UserRepository users) {
		this.users = users;
	}

	@ModelAttribute
	void account(@AuthenticationPrincipal SrrrgPrincipal principal, HttpServletRequest request, Model model) {
		if (principal != null) {
			users.findById(principal.userId()).ifPresent(user ->
					model.addAttribute("accountUser", new AccountUser(user.getDisplayName(), user.getEmail())));
		}
		model.addAttribute("accountReturnTo", request.getRequestURI());
	}

	record AccountUser(String displayName, String email) { }
}

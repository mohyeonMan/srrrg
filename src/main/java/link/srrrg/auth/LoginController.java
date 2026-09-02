package link.srrrg.auth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 로그인 화면을 렌더한다. 인증 자체는 하지 않고, 로그인 후 돌아갈 경로만 화면에 넘긴다.
 * 그 값의 검증은 {@code DatabaseAuthorizationRequestRepository}가 인가 요청을 저장할 때 수행한다.
 */
@Controller
class LoginController {

	@GetMapping("/login")
	String login(@RequestParam(defaultValue = "/") String returnTo, Model model) {
		model.addAttribute("returnTo", returnTo);
		return "login";
	}
}

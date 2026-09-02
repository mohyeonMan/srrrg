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
/**
 * 초대 링크를 열었을 때 보여줄 화면. 로그인 전에도 접근할 수 있어야 해서 인가 규칙에서 열려 있고,
 * 수락은 이 화면이 아니라 로그인 후 별도 API 호출로 이루어진다.
 * 미리보기는 유효하지 않은 토큰에도 같은 화면을 렌더하므로 토큰 유효성만 드러난다.
 */
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

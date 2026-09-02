package link.srrrg;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 템플릿만 렌더하는 화면 라우트. 데이터는 화면 스크립트가 별도 API 호출로 가져오므로
 * 여기서는 모델을 거의 채우지 않는다.
 *
 * <p>모든 경로가 인증 없이 열려 있다. 화면 자체는 로그인 없이도 뜨고, 보호가 필요한 데이터는
 * 그 화면이 호출하는 {@code /api/web/**}에서 막힌다.</p>
 */
@Controller
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "index";
	}

	/**
	 * 익명 링크 관리 화면. 쿼리로 받은 코드를 입력란에 미리 채워 주기만 하고,
	 * 실제 조회는 화면이 secret key와 함께 API를 호출해 수행한다. 여기서는 코드의 유효성을 확인하지 않는다.
	 */
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

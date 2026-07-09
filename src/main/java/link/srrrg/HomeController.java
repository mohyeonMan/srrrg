package link.srrrg;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "index";
	}

	@GetMapping("/favicon.ico")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void favicon() {
	}
}

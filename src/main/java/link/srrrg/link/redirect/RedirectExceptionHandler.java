package link.srrrg.link.redirect;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;

@ControllerAdvice(assignableTypes = RedirectController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RedirectExceptionHandler {

	@ExceptionHandler(LinkNotFoundException.class)
	public String handleLinkNotFound(
			LinkNotFoundException exception,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		return errorPage(
				HttpStatus.NOT_FOUND,
				"링크를 찾을 수 없습니다",
				exception.getMessage(),
				request,
				response,
				model
		);
	}

	@ExceptionHandler(LinkGoneException.class)
	public String handleLinkGone(
			LinkGoneException exception,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		return errorPage(
				HttpStatus.GONE,
				"사용할 수 없는 링크입니다",
				exception.getMessage(),
				request,
				response,
				model
		);
	}

	private String errorPage(
			HttpStatus status,
			String title,
			String message,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		response.setStatus(status.value());
		// 존재하지 않거나 만료된 링크의 오류 페이지도 캐시하지 않음.
		response.setHeader("Cache-Control", "no-store");
		model.addAttribute("status", status.value());
		model.addAttribute("title", title);
		model.addAttribute("message", message);
		model.addAttribute("shortUrl", request.getRequestURL().toString());
		return "redirect-error";
	}
}

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
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;

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

	@ExceptionHandler(UnsafeUrlException.class)
	public String handleUnsafeUrl(
			UnsafeUrlException exception,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		model.addAttribute("safeBrowsingAdvisory", true);
		return errorPage(
				HttpStatus.FORBIDDEN,
				"잠재적으로 위험한 링크입니다",
				"Google Safe Browsing에서 알려진 피싱 또는 악성 사이트로 분류되어 이동을 차단했습니다.",
				request,
				response,
				model
		);
	}

	@ExceptionHandler(UrlRiskCheckFailedException.class)
	public String handleUrlRiskCheckFailed(
			UrlRiskCheckFailedException exception,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		response.setHeader("Retry-After", "30");
		return errorPage(
				HttpStatus.SERVICE_UNAVAILABLE,
				"링크를 확인할 수 없습니다",
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
		// 리다이렉트 오류와 검증 결과가 브라우저나 중간 캐시에 남지 않게 함.
		response.setHeader("Cache-Control", "no-store");
		model.addAttribute("status", status.value());
		model.addAttribute("title", title);
		model.addAttribute("message", message);
		model.addAttribute("shortUrl", request.getRequestURL().toString());
		model.addAttribute("retryable", status == HttpStatus.SERVICE_UNAVAILABLE);
		return "redirect-error";
	}
}

package link.srrrg.link.redirect;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.WebAccountModel;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;

@ControllerAdvice(assignableTypes = RedirectController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RedirectExceptionHandler {

	private final WebAccountModel webAccountModel;

	public RedirectExceptionHandler(WebAccountModel webAccountModel) {
		this.webAccountModel = webAccountModel;
	}

	@ExceptionHandler(LinkNotFoundException.class)
	public String handleLinkNotFound(
			LinkNotFoundException exception,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model
	) {
		// 예외 메시지는 제목과 같은 문장이라 화면에서는 설명이 제목을 되풀이했다.
		// 설명 자리에는 원인과 다음 행동을 담는다. 예외 메시지는 로그와 API 응답용으로 그대로 둔다.
		return errorPage(
				HttpStatus.NOT_FOUND,
				"링크를 찾을 수 없습니다",
				"주소를 다시 확인해 주세요. 존재하지 않는 주소이거나 이미 삭제된 링크입니다.",
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
		String title = switch (exception.getReason()) {
			case EXPIRED -> "만료된 링크입니다";
			case NO_DESTINATION -> "목적지가 없는 링크입니다";
			case UNKNOWN -> "사용할 수 없는 링크입니다";
		};
		return errorPage(
				HttpStatus.GONE,
				title,
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
		// @ControllerAdvice 의 @ModelAttribute 는 예외 핸들러가 렌더하는 뷰에 적용되지 않는다.
		// 직접 채워 주지 않으면 로그인 상태에서도 헤더가 로그아웃으로 보인다.
		webAccountModel.apply(request, model);
		response.setStatus(status.value());
		// 리다이렉트 오류와 검증 결과가 브라우저나 중간 캐시에 남지 않게 함.
		response.setHeader("Cache-Control", "no-store");
		model.addAttribute("status", status.value());
		model.addAttribute("title", title);
		model.addAttribute("message", message);
		model.addAttribute("shortUrl", request.getRequestURL().toString());
		model.addAttribute("retryable", status == HttpStatus.SERVICE_UNAVAILABLE);
		model.addAttribute("retryAfterSeconds", status == HttpStatus.SERVICE_UNAVAILABLE ? 30 : 0);
		model.addAttribute("statusKind", statusKind(status));
		model.addAttribute("statusLabel", statusLabel(status));
		return "redirect-error";
	}

	private String statusKind(HttpStatus status) {
		return switch (status) {
			case FORBIDDEN -> "blocked";
			case GONE -> "gone";
			case SERVICE_UNAVAILABLE -> "retryable";
			default -> "not-found";
		};
	}

	private String statusLabel(HttpStatus status) {
		return switch (status) {
			case FORBIDDEN -> "이동 차단";
			case GONE -> "사용 종료";
			case SERVICE_UNAVAILABLE -> "일시적 오류";
			default -> "링크 없음";
		};
	}
}

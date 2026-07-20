package link.srrrg.link.redirect;

import java.net.URI;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;
import link.srrrg.link.redirect.dto.RedirectLink;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RedirectController {

	private static final String CSP = "default-src 'self'; script-src 'self'; connect-src 'self'; "
			+ "object-src 'none'; base-uri 'none'; frame-ancestors 'none';";

	private final RedirectService linkService;
	private final ClientRequestInfoResolver requestInfoResolver;

	@GetMapping("/{code:[0-9A-Za-z]{6}}")
	public String redirect(@PathVariable String code, HttpServletRequest request,
			HttpServletResponse response, Model model) {
		// 외부 사이트로 바로 보내지 않고 모든 접근에 Secure Redirect 페이지를 반환함.
		ClientRequestInfo requestInfo = requestInfoResolver.resolve(request);
		RedirectLink link = linkService.resolveRedirectPage(code, requestInfo);
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Content-Security-Policy", CSP);
		model.addAttribute("code", link.code());
		model.addAttribute("checkUrl", "/api/redirect-check/" + link.code());
		model.addAttribute("shortUrl", request.getRequestURL().toString());
		model.addAttribute("originalUrl", link.originalUrl());
		model.addAttribute("originalUrlHost", URI.create(link.originalUrl()).getHost());
		model.addAttribute("cachedStatus", link.cachedStatus());
		// 값이 있더라도 외부 URL이 아니라 srrrg가 302를 발급하는 내부 URL임.
		model.addAttribute("cachedRedirectUrl", link.cachedRedirectUrl());
		return "redirect-confirm";
	}
}

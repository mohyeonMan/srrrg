package link.srrrg.link;

import java.net.URI;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.link.dto.RedirectLink;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RedirectController {

	private static final String CSP = "default-src 'self'; script-src 'self'; connect-src 'self'; "
			+ "object-src 'none'; base-uri 'none'; frame-ancestors 'none';";

	private final LinkService linkService;
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
		// 캐시된 위협 결과에는 목적지 URL을 전달하지 않음.
		model.addAttribute("cachedRedirectUrl",
				link.cachedStatus() == LinkStatus.NO_THREAT_FOUND || link.cachedStatus() == LinkStatus.CHECK_FAILED
						? link.originalUrl() : null);
		return "redirect-confirm";
	}
}

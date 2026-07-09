package link.srrrg.link;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;
import link.srrrg.link.dto.RedirectLink;
import lombok.RequiredArgsConstructor;

@Controller
@Tag(name = "Redirect", description = "단축 링크 리다이렉트 API")
@RequiredArgsConstructor
public class RedirectController {

	private final LinkService linkService;
	private final ClientRequestInfoResolver requestInfoResolver;

	@GetMapping("/{code:[0-9A-Za-z]{6}}")
	@Operation(summary = "원본 URL로 이동", description = "단축 코드에 연결된 원본 URL로 302 리다이렉트합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "302", description = "원본 URL로 이동"),
			@ApiResponse(responseCode = "404", description = "존재하지 않는 코드"),
			@ApiResponse(responseCode = "410", description = "삭제되었거나 만료된 링크")
	})
	public Object redirect(
			@Parameter(description = "6자리 Base62 단축 코드", example = "aB3x9Q")
			@PathVariable String code,
			HttpServletRequest request,
			Model model
	) {
		ClientRequestInfo requestInfo = requestInfoResolver.resolve(request);
		RedirectLink redirectLink = linkService.resolveRedirect(code, requestInfo);
		if (!redirectLink.trusted()) {
			model.addAttribute("code", redirectLink.code());
			model.addAttribute("shortUrl", request.getRequestURL().toString());
			model.addAttribute("originalUrl", redirectLink.originalUrl());
			model.addAttribute("originalUrlSummary", summarizeUrl(redirectLink.originalUrl()));
			return "redirect-confirm";
		}

		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(redirectLink.originalUrl()))
				.build();
	}

	@PostMapping("/{code:[0-9A-Za-z]{6}}/redirect")
	@Operation(summary = "확인 후 원본 URL로 이동", description = "확인 페이지에서 사용자가 이동을 선택하면 원본 URL로 302 리다이렉트합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "302", description = "원본 URL로 이동"),
			@ApiResponse(responseCode = "404", description = "존재하지 않는 코드"),
			@ApiResponse(responseCode = "410", description = "삭제되었거나 만료된 링크")
	})
	public ResponseEntity<Void> confirmRedirect(
			@Parameter(description = "6자리 Base62 단축 코드", example = "aB3x9Q")
			@PathVariable String code,
			HttpServletRequest request
	) {
		ClientRequestInfo requestInfo = requestInfoResolver.resolve(request);
		RedirectLink redirectLink = linkService.confirmRedirect(code, requestInfo);
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(redirectLink.originalUrl()))
				.build();
	}

	private String summarizeUrl(String value) {
		URI uri = URI.create(value);
		String authority = uri.getRawAuthority();
		if (authority == null || authority.isBlank()) {
			return value;
		}
		return uri.getScheme() + "://" + authority + "/...";
	}
}

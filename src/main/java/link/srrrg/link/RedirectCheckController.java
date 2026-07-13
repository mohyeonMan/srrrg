package link.srrrg.link;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.link.access.ClientRequestInfoResolver;
import link.srrrg.link.dto.RedirectCheckResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RedirectCheckController {

	private final LinkService linkService;
	private final ClientRequestInfoResolver requestInfoResolver;

	@PostMapping("/api/redirect-check/{code:[0-9A-Za-z]{6}}")
	public ResponseEntity<RedirectCheckResponse> check(@PathVariable String code, HttpServletRequest request) {
		// 임의 URL을 받지 않고 short code로 현재 저장 URL을 조회해 검사함.
		RedirectCheckResponse result = linkService.checkRedirect(code, requestInfoResolver.resolve(request));
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(result);
	}

	@GetMapping("/api/redirect/{code:[0-9A-Za-z]{6}}")
	public ResponseEntity<Void> redirect(@PathVariable String code, @RequestParam String ticket,
			HttpServletRequest request) {
		String redirectUrl = linkService.redirectToOriginal(code, ticket, requestInfoResolver.resolve(request));
		return ResponseEntity.status(HttpStatus.FOUND)
				.cacheControl(CacheControl.noStore())
				.location(URI.create(redirectUrl))
				.build();
	}
}

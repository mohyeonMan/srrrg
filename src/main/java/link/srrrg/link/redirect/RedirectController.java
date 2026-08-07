package link.srrrg.link.redirect;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.link.access.ClientRequestInfoResolver;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class RedirectController {

	private final RedirectService redirectService;
	private final ClientRequestInfoResolver requestInfoResolver;

	@GetMapping("/{code:[0-9A-Za-z]{6}}")
	public ResponseEntity<Void> redirect(@PathVariable String code,
			@RequestHeader("Host") String host, HttpServletRequest request) {
		String originalUrl = redirectService.redirect(host, code, requestInfoResolver.resolve(request));
		return ResponseEntity.status(HttpStatus.FOUND)
				.cacheControl(CacheControl.noStore())
				.location(URI.create(originalUrl))
				.build();
	}
}

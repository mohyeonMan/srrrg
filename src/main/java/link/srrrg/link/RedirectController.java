package link.srrrg.link;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;

@RestController
@Tag(name = "Redirect", description = "단축 링크 리다이렉트 API")
public class RedirectController {

	private final LinkService linkService;
	private final ClientRequestInfoResolver requestInfoResolver;

	public RedirectController(LinkService linkService, ClientRequestInfoResolver requestInfoResolver) {
		this.linkService = linkService;
		this.requestInfoResolver = requestInfoResolver;
	}

	@GetMapping("/{code:[0-9A-Za-z]{6}}")
	@Operation(summary = "원본 URL로 이동", description = "단축 코드에 연결된 원본 URL로 302 리다이렉트합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "302", description = "원본 URL로 이동"),
			@ApiResponse(responseCode = "404", description = "존재하지 않는 코드"),
			@ApiResponse(responseCode = "410", description = "삭제되었거나 만료된 링크")
	})
	public ResponseEntity<Void> redirect(
			@Parameter(description = "6자리 Base62 단축 코드", example = "aB3x9Q")
			@PathVariable String code,
			HttpServletRequest request
	) {
		ClientRequestInfo requestInfo = requestInfoResolver.resolve(request);
		String originalUrl = linkService.resolveRedirect(code, requestInfo);
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(originalUrl))
				.build();
	}
}

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

/**
 * 공개 리다이렉트 진입점. 인증이 없고 트래픽이 가장 많은 경로다.
 *
 * <p>코드 패턴을 여섯 자 영숫자로 제한해 매핑한다. 이 제약이 없으면 정적 자원이나 다른 화면 경로까지
 * 이 핸들러가 가져가 실제 페이지 대신 404 리다이렉트 오류가 뜬다.</p>
 *
 * <p>{@code Host} 헤더를 받는 것은 코드 공간이 호스트별로 나뉘기 때문이다. 같은 코드라도
 * 베이스 도메인과 프로젝트 서브도메인에서 서로 다른 링크를 가리킬 수 있다.</p>
 *
 * <p>302를 쓰는 것은 목적지가 언제든 바뀔 수 있어서다. 301로 보내면 브라우저가 결과를 캐시해
 * 목적지를 고쳐도 예전 주소로 계속 이동하고, 접근 통계도 더 이상 잡히지 않는다.
 * 같은 이유로 {@code no-store}를 직접 붙인다.</p>
 */
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

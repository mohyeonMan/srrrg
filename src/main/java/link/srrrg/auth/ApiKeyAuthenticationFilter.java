package link.srrrg.auth;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.common.ratelimit.RateLimitExceededException;
import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.project.ApiKeyService;

/**
 * {@code /api/v1/**} 표면의 인증과 읽기 레이트리밋을 담당한다.
 *
 * <p>이 표면의 오류는 RFC 7807 {@code ProblemDetail} 형식이어야 하는데, Spring Security의 기본
 * 인증 실패 응답은 그 형식이 아니다. 그래서 인증을 인가 규칙에 맡기지 않고 필터가 직접 검사한 뒤
 * 실패 시 체인을 진행시키지 않고 응답을 완성한다.</p>
 *
 * <p>인증에 성공하면 주체를 {@code SecurityContext}와 request 속성 양쪽에 넣는다.
 * 컨트롤러가 프로젝트 범위를 확인할 때 request 속성 {@code srrrg.apiKeyPrincipal}에서 꺼내 쓴다.</p>
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
	private final ApiKeyService keys;
	private final RateLimitService rateLimitService;

	ApiKeyAuthenticationFilter(ApiKeyService keys, RateLimitService rateLimitService) {
		this.keys = keys;
		this.rateLimitService = rateLimitService;
	}

	/**
	 * v1 경로 밖에서는 아예 동작하지 않는다. 이 조건이 넓어지면 웹 화면 요청까지 API 키를 요구하게 된다.
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		// 접두사까지 확인해 형태가 다른 값은 키 조회에 들어가기 전에 끊는다.
		// 없는 키와 형식이 틀린 키를 같은 401 문구로 합쳐 유효한 키 형태를 추측할 단서를 주지 않는다.
		if (authorization == null || !authorization.startsWith("Bearer srrrg_pk_")) {
			ApiProblemWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "API_KEY_INVALID",
					"유효한 API key가 필요합니다.");
			return;
		}
		try {
			ApiKeyService.ApiKeyPrincipal principal = keys.authenticate(authorization.substring("Bearer ".length()));
			// 읽기만 여기서 제한한다. 쓰기 한도는 요청 본문을 해석한 뒤 프로젝트 단위로 걸어야 해서
			// 서비스 계층이 담당하며, 여기서 중복으로 세면 한 요청이 두 번 차감된다.
			if ("GET".equalsIgnoreCase(request.getMethod())) {
				try {
					rateLimitService.checkApiKeyRead(principal.keyId());
				} catch (RateLimitExceededException exception) {
					response.setHeader("Retry-After", String.valueOf(exception.getRetryAfterSeconds()));
					ApiProblemWriter.write(response, 429, "RATE_LIMIT_EXCEEDED", exception.getMessage());
					return;
				}
			}
			// 외부 연동에서 특정 요청을 지목해 문의할 수 있도록 응답마다 식별자를 붙인다.
			response.setHeader("X-Request-Id", UUID.randomUUID().toString());
			request.setAttribute("srrrg.apiKeyPrincipal", principal);
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
			SecurityContextHolder.setContext(context);
			chain.doFilter(request, response);
			// 폐기·만료·미존재 키를 구분하지 않고 같은 응답으로 합친다.
			// 컨텍스트를 비우는 것은 인증 실패 상태가 이 스레드의 다음 처리에 남지 않게 하려는 것이다.
		} catch (ApiKeyService.ApiKeyUnauthorizedException exception) {
			SecurityContextHolder.clearContext();
			ApiProblemWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "API_KEY_INVALID",
					"유효한 API key가 필요합니다.");
		}
	}
}

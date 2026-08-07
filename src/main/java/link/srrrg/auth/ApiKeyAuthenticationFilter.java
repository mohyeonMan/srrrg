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

class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
	private final ApiKeyService keys;
	private final RateLimitService rateLimitService;
	ApiKeyAuthenticationFilter(ApiKeyService keys, RateLimitService rateLimitService) {
		this.keys = keys;
		this.rateLimitService = rateLimitService;
	}
	@Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/"); }
	@Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer srrrg_pk_")) {
			ApiProblemWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "API_KEY_INVALID", "유효한 API key가 필요합니다."); return;
		}
		try {
			ApiKeyService.ApiKeyPrincipal principal = keys.authenticate(authorization.substring("Bearer ".length()));
			if ("GET".equalsIgnoreCase(request.getMethod())) {
				try {
					rateLimitService.checkApiKeyRead(principal.keyId());
				} catch (RateLimitExceededException exception) {
					response.setHeader("Retry-After", String.valueOf(exception.getRetryAfterSeconds()));
					ApiProblemWriter.write(response, 429, "RATE_LIMIT_EXCEEDED", exception.getMessage());
					return;
				}
			}
			response.setHeader("X-Request-Id", UUID.randomUUID().toString());
			request.setAttribute("srrrg.apiKeyPrincipal", principal);
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
			SecurityContextHolder.setContext(context);
			chain.doFilter(request, response);
		} catch (ApiKeyService.ApiKeyUnauthorizedException exception) {
			SecurityContextHolder.clearContext();
			ApiProblemWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "API_KEY_INVALID", "유효한 API key가 필요합니다.");
		}
	}
}

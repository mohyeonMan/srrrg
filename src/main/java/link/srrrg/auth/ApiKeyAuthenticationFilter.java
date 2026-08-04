package link.srrrg.auth;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.project.ApiKeyService;

@Component
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
	private final ApiKeyService keys;
	ApiKeyAuthenticationFilter(ApiKeyService keys) { this.keys = keys; }
	@Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/v1/"); }
	@Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer srrrg_pk_")) {
			ApiProblemWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "API_KEY_INVALID", "유효한 API key가 필요합니다."); return;
		}
		try {
			ApiKeyService.ApiKeyPrincipal principal = keys.authenticate(authorization.substring("Bearer ".length()));
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

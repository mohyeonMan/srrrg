package link.srrrg.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (request.getRequestURI().startsWith("/api/v1/")) {
			filterChain.doFilter(request, response);
			return;
		}
		String token = cookie(request, WebTokenCookies.ACCESS_COOKIE);
		if (token != null) {
			try {
				SrrrgPrincipal principal = new SrrrgPrincipal(jwtService.verify(token));
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(principal, token, List.of()));
			} catch (IllegalArgumentException ignored) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

	static String cookie(HttpServletRequest request, String name) {
		if (request.getCookies() == null) {
			return null;
		}
		return Arrays.stream(request.getCookies())
				.filter(cookie -> name.equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}
}

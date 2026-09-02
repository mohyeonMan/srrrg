package link.srrrg.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
/**
 * 쿠키의 access token을 읽어 웹 요청의 {@code SecurityContext}를 채운다.
 *
 * <p>토큰이 없거나 잘못돼도 여기서 요청을 끊지 않는다. 인증 없이 통과시키고, 실제로 인증이 필요한지는
 * 뒤따르는 인가 규칙이 경로에 따라 판단한다. 공개 화면과 리다이렉트가 같은 체인을 지나기 때문이다.</p>
 *
 * <p>검증에 실패하면 컨텍스트를 비운다. 앞선 요청의 인증 상태가 스레드에 남아 다음 요청에 새는 것을 막는다.</p>
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		// API 키 표면은 쿠키를 보지 않는다. 브라우저에 로그인 세션이 남아 있는 상태로 v1을 호출해도
		// 그 쿠키로 주체가 정해지면 안 되고, 반드시 Authorization 헤더의 키로만 인증돼야 한다.
		if (request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/")) {
			filterChain.doFilter(request, response);
			return;
		}
		String token = cookie(request, WebTokenCookies.ACCESS_COOKIE);
		if (token != null) {
			try {
				SrrrgPrincipal principal = new SrrrgPrincipal(jwtService.verify(token));
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, token, List.of()));
				SecurityContextHolder.setContext(context);
				// 만료·위조 토큰은 익명 요청과 같게 취급한다. 여기서 401을 던지면 로그인 없이 볼 수 있는 화면도
				// 오래된 쿠키 하나 때문에 막힌다.
			} catch (IllegalArgumentException ignored) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * 이름이 같은 첫 쿠키의 값을 돌려준다. 같은 이름의 쿠키가 경로만 다르게 여러 개 올 수 있는데
	 * 서블릿 API는 그 경로를 알려주지 않으므로 첫 값을 쓴다.
	 * 이 필터 밖에서도 쿠키를 읽어야 하는 곳이 있어 정적 메서드로 열어 두었다.
	 */
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

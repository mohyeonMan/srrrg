package link.srrrg.auth;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CSRF 토큰을 실제로 만들어 응답 쿠키에 실리게 하는 필터.
 *
 * <p>Spring Security는 토큰 생성을 지연시켜 두기 때문에, 아무도 값을 읽지 않으면 쿠키가 내려가지 않는다.
 * 화면이 첫 GET에서 쿠키를 받아 두어야 이후 쓰기 요청에 헤더를 실을 수 있으므로,
 * 여기서 값을 한 번 꺼내 생성을 강제한다. 반환값을 쓰지 않는 호출이지만 그것이 목적이다.</p>
 */
class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			csrfToken.getToken();
		}
		filterChain.doFilter(request, response);
	}
}

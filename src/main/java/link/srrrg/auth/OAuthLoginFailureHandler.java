package link.srrrg.auth;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
/**
 * OAuth 로그인 실패를 사용자에게는 로그인 화면으로, 운영에는 로그로 남긴다.
 * 공급자가 준 오류 코드와 메시지는 로그에만 남기고 화면에는 노출하지 않는다.
 * 그 문구에 공급자 설정이나 클라이언트 정보가 섞여 나올 수 있기 때문이다.
 */
class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {
		String code = exception instanceof OAuth2AuthenticationException oauth
				? oauth.getError().getErrorCode() : exception.getClass().getSimpleName();
		log.warn("OAuth login failed: callback={}, code={}, reason={}",
				request.getRequestURI(), code, exception.getMessage(), exception);
		response.sendRedirect(request.getContextPath() + "/login?error=oauth");
	}
}

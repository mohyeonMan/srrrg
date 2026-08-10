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

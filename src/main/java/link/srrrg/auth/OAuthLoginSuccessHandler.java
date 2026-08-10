package link.srrrg.auth;

import java.io.IOException;
import java.time.Duration;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.auth.OAuthAccountLinkService.PendingLink;
import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthIdentityService;
import link.srrrg.identity.OAuthIdentityService.LoginResolution;
import link.srrrg.identity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final String LINK_COOKIE = "srrrg_oauth_link";

	private final OAuthIdentityService identityService;
	private final OAuthAccountLinkService accountLinkService;
	private final WebSessionService sessionService;
	private final WebTokenCookies tokenCookies;
	private final DatabaseAuthorizationRequestRepository authorizationRequests;
	private final OAuth2AuthorizedClientService authorizedClientService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
		try {
			OAuthIdentity identity = OAuthProfileFactory.from(oauth);
			LoginResolution resolution = identityService.resolve(identity);
			String returnPath = returnPath(request);
			if (resolution.requiresLink()) {
				PendingLink pending = accountLinkService.create(
						resolution.user(), resolution.pendingIdentity(), returnPath);
				authorizationRequests.addCookie(
						response, LINK_COOKIE, pending.rawToken(), Duration.ofMinutes(10));
				redirect(request, response, "/login?link_required=true");
				return;
			}
			User user = resolution.user();
			String pendingToken = JwtAuthenticationFilter.cookie(request, LINK_COOKIE);
			if (pendingToken != null) {
				returnPath = accountLinkService.complete(pendingToken, user);
				authorizationRequests.addCookie(response, LINK_COOKIE, "", Duration.ZERO);
			}
			tokenCookies.write(request, response, sessionService.issue(user));
			redirect(request, response, destination(user, returnPath));
		} catch (IllegalArgumentException | IllegalStateException exception) {
			log.warn("OAuth login processing failed: provider={}, reason={}",
					oauth.getAuthorizedClientRegistrationId(), exception.getMessage(), exception);
			tokenCookies.clear(request, response);
			redirect(request, response, "/login?error=oauth");
		} finally {
			authorizedClientService.removeAuthorizedClient(
					oauth.getAuthorizedClientRegistrationId(), oauth.getName());
		}
	}

	private String returnPath(HttpServletRequest request) {
		Object path = request.getAttribute(DatabaseAuthorizationRequestRepository.RETURN_PATH_ATTRIBUTE);
		return path instanceof String value ? value : "/";
	}

	static String destination(User user, String returnPath) {
		return user.needsOnboarding()
				? UriComponentsBuilder.fromPath("/onboarding").queryParam("returnTo", returnPath)
						.build().encode().toUriString()
				: returnPath;
	}

	private void redirect(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
		response.sendRedirect(request.getContextPath() + path);
	}
}

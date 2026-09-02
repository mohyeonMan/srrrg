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

/**
 * 공급자 인증이 끝난 직후, 그 결과를 이 서비스의 사용자와 세션으로 바꾼다.
 *
 * <p>세 갈래로 나뉜다. 이미 연결된 계정이면 바로 세션을 발급하고, 검증된 이메일이 기존 사용자와 같으면
 * 자동으로 합치지 않고 계정 연결 확인 절차로 보내며, 그 외에는 새 사용자를 만든다.
 * 두 번째 경우를 확인 없이 합치면 남의 이메일을 등록해 둔 공급자 계정으로 그 사용자의 자원에 들어갈 수 있다.</p>
 *
 * <p>실패는 예외를 밖으로 던지지 않고 로그인 화면으로 되돌린다. 이 시점은 리다이렉트 응답을 쓰는 중이라
 * 예외 처리기가 JSON을 쓰면 사용자에게 깨진 화면이 보인다.</p>
 *
 * <p>{@code finally}에서 인가된 클라이언트를 제거하는 것은 공급자 access token을 메모리에 남기지 않기 위해서다.
 * 로그인 이후에는 이 서비스의 세션만 쓰므로 공급자 토큰을 계속 들고 있을 이유가 없고,
 * 저장소가 파드 로컬 메모리라 파드마다 다르게 쌓인다.</p>
 */
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
			// 같은 검증된 이메일을 쓰는 기존 사용자가 있는 경우다. 여기서 바로 로그인시키지 않고
			// 대기 토큰만 쿠키로 준 뒤, 기존 방식으로 다시 로그인해 본인임을 증명하게 한다.
			if (resolution.requiresLink()) {
				PendingLink pending = accountLinkService.create(
						resolution.user(), resolution.pendingIdentity(), returnPath);
				authorizationRequests.addCookie(
						response, LINK_COOKIE, pending.rawToken(), Duration.ofMinutes(10));
				redirect(request, response, "/login?link_required=true");
				return;
			}
			User user = resolution.user();
			// 위 단계에서 안내받고 기존 계정으로 다시 로그인해 돌아온 경우다. 이번 로그인의 주체가
			// 대기 요청이 지목한 사용자와 일치할 때만 연결이 성사되며, 확인은 연결 서비스가 한다.
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

	/**
	 * 로그인 후 보낼 위치를 정한다. 온보딩을 마치지 않았으면 원래 목적지를 쿼리로 넘기며 온보딩 화면을 먼저 거친다.
	 * 목적지는 이미 검증된 내부 경로지만, 쿼리 파라미터로 들어가므로 인코딩해서 붙인다.
	 */
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

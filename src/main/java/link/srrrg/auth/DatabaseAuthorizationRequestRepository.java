package link.srrrg.auth;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.common.util.SecureRandomStringGenerator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DatabaseAuthorizationRequestRepository
		implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	public static final String RETURN_PATH_ATTRIBUTE = DatabaseAuthorizationRequestRepository.class.getName() + ".returnPath";
	private static final String COOKIE_PREFIX = "srrrg_oauth_request_";
	private static final String REGISTRATION_ID = "registration_id";
	private static final String URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	private static final Duration LIFETIME = Duration.ofMinutes(10);

	private final OAuthAuthorizationRequestRepository repository;
	private final SecureRandomStringGenerator random;

	@Override
	@Transactional(readOnly = true)
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		String raw = cookieValue(request, cookieName(request.getParameter("state")));
		if (raw == null) {
			return null;
		}
		return repository.findByTokenHash(TokenHash.sha256(raw))
				.filter(stored -> stored.getExpiresAt().isAfter(Instant.now()))
				.map(this::restore)
				.orElse(null);
	}

	@Override
	@Transactional
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
			HttpServletRequest request, HttpServletResponse response) {
		if (authorizationRequest == null) {
			clearCookie(request, response);
			return;
		}
		repository.deleteExpired(Instant.now());
		String codeVerifier = authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER);
		Object codeChallenge = authorizationRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE);
		String registrationId = authorizationRequest.getAttribute(REGISTRATION_ID);
		if (codeVerifier == null || codeChallenge == null || registrationId == null) {
			throw new IllegalStateException("OAuth state와 PKCE 요청을 저장할 수 없습니다.");
		}
		String raw = random.generate(URL_SAFE, 43);
		repository.save(OAuthAuthorizationRequest.create(
				TokenHash.sha256(raw),
				authorizationRequest.getState(),
				registrationId,
				authorizationRequest.getAuthorizationUri(),
				authorizationRequest.getClientId(),
				authorizationRequest.getRedirectUri(),
				String.join(" ", authorizationRequest.getScopes()),
				codeVerifier,
				codeChallenge.toString(),
				returnPath(request.getParameter("returnTo")),
				Instant.now().plus(LIFETIME)));
		addCookie(response, cookieName(authorizationRequest.getState()), raw, LIFETIME);
	}

	@Override
	@Transactional
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
			HttpServletResponse response) {
		String cookieName = cookieName(request.getParameter("state"));
		String raw = cookieValue(request, cookieName);
		clearCookie(cookieName, response);
		if (raw == null) {
			return null;
		}
		return repository.findByTokenHash(TokenHash.sha256(raw))
				.filter(stored -> stored.getExpiresAt().isAfter(Instant.now()))
				.map(stored -> {
					request.setAttribute(RETURN_PATH_ATTRIBUTE, stored.getReturnPath());
					repository.delete(stored);
					return restore(stored);
				})
				.orElse(null);
	}

	private OAuth2AuthorizationRequest restore(OAuthAuthorizationRequest stored) {
		Set<String> scopes = Set.copyOf(Arrays.asList(stored.getScopes().split(" ")));
		return OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri(stored.getAuthorizationUri())
				.clientId(stored.getClientId())
				.redirectUri(stored.getRedirectUri())
				.scopes(scopes)
				.state(stored.getState())
				.attributes(attributes -> {
					attributes.put(REGISTRATION_ID, stored.getRegistrationId());
					attributes.put(PkceParameterNames.CODE_VERIFIER, stored.getCodeVerifier());
				})
				.additionalParameters(parameters -> {
					parameters.put(PkceParameterNames.CODE_CHALLENGE, stored.getCodeChallenge());
					parameters.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256");
				})
				.build();
	}

	private String cookieValue(HttpServletRequest request, String name) {
		if (name == null || request.getCookies() == null) {
			return null;
		}
		return Arrays.stream(request.getCookies())
				.filter(cookie -> name.equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}

	private String returnPath(String supplied) {
		if (supplied == null || supplied.isBlank()) {
			return "/";
		}
		URI uri;
		try {
			uri = URI.create(supplied);
		} catch (IllegalArgumentException exception) {
			return "/";
		}
		return supplied.startsWith("/") && !supplied.startsWith("//")
				&& uri.getScheme() == null && uri.getHost() == null && supplied.length() <= 1000
				? supplied : "/";
	}

	private String cookieName(String state) {
		return state == null ? null : COOKIE_PREFIX + TokenHash.sha256(state).substring(0, 16);
	}

	private void clearCookie(HttpServletRequest request, HttpServletResponse response) {
		clearCookie(cookieName(request.getParameter("state")), response);
	}

	private void clearCookie(String name, HttpServletResponse response) {
		if (name != null) addCookie(response, name, "", Duration.ZERO);
	}

	static void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(maxAge)
				.build().toString());
	}
}

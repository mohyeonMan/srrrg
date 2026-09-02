package link.srrrg.auth;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.common.util.SecureRandomStringGenerator;

/**
 * OAuth 인가 요청(state, PKCE code_verifier, 돌아갈 경로)을 브라우저 세션 대신 DB에 보관한다.
 *
 * <p>Spring Security의 기본 구현은 이 값을 HTTP 세션에 담는데, 이 서비스는 세션이 STATELESS라 쓸 수 없다.
 * 공급자에서 돌아오는 콜백이 인가 요청을 시작한 파드와 다른 파드에 도착할 수 있으므로,
 * 모든 파드가 함께 보는 DB에 두어야 한다.</p>
 *
 * <p>브라우저에는 조회용 난수만 쿠키로 준다. 쿠키 값의 해시로 행을 찾으므로 DB에는 원문이 남지 않고,
 * 쿠키를 가진 브라우저만 자신이 시작한 인가 요청을 복원할 수 있다. state 값만으로 조회하게 두면
 * 콜백 URL의 state를 흉내 낸 요청이 남의 인가 요청을 집어갈 수 있다.</p>
 *
 * <p>code_verifier는 해시가 아니라 원문으로 저장한다. 토큰 교환 때 공급자에게 그대로 보내야 하는 값이라
 * 되돌릴 수 없으면 로그인 자체가 성립하지 않는다. 대신 유효기간을 10분으로 짧게 두고 사용 즉시 삭제한다.</p>
 */
@Component
public class DatabaseAuthorizationRequestRepository
		implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	public static final String RETURN_PATH_ATTRIBUTE = DatabaseAuthorizationRequestRepository.class.getName() + ".returnPath";
	private static final String COOKIE_PREFIX = "srrrg_oauth_request_";
	private static final String REGISTRATION_ID = "registration_id";
	private static final String URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	// 인가 요청은 사용자가 공급자 화면을 거쳐 돌아오는 동안만 살아 있으면 된다.
	// 길게 두면 쓰이지 않은 code_verifier가 오래 남으므로 10분으로 제한한다.
	private static final Duration LIFETIME = Duration.ofMinutes(10);

	private final OAuthAuthorizationRequestRepository repository;
	private final SecureRandomStringGenerator random;
	private final boolean secureCookies;

	DatabaseAuthorizationRequestRepository(OAuthAuthorizationRequestRepository repository,
			SecureRandomStringGenerator random, @Value("${srrrg.base-url}") String baseUrl) {
		this.repository = repository;
		this.random = random;
		this.secureCookies = URI.create(baseUrl).getScheme().equalsIgnoreCase("https");
	}

	/**
	 * 콜백 요청에서 진행 중인 인가 요청을 복원한다. 쿠키가 없거나 만료됐으면 {@code null}을 돌려주고,
	 * Spring Security는 이를 인가 요청 없음으로 보고 로그인 실패로 처리한다.
	 * 삭제는 하지 않으므로 같은 요청에서 여러 번 호출돼도 안전하다.
	 */
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

	/**
	 * 로그인 시작 시점의 인가 요청을 저장하고 조회용 쿠키를 내려보낸다.
	 *
	 * @param authorizationRequest Spring Security가 만든 인가 요청. {@code null}이면 진행 중인 요청을
	 *                             지우라는 뜻이므로 쿠키만 제거한다
	 * @throws IllegalStateException PKCE 값이나 registrationId가 없는 경우. 그대로 진행하면
	 *                               토큰 교환 단계에서야 실패하므로 여기서 끊는다
	 */
	@Override
	@Transactional
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
			HttpServletRequest request, HttpServletResponse response) {
		if (authorizationRequest == null) {
			clearCookie(request, response);
			return;
		}
		// 만료된 행을 여기서 함께 정리한다. 별도 스케줄러를 두면 모든 파드에서 중복 실행되므로,
		// 로그인 시도라는 자연스러운 계기에 묻어서 처리한다.
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

	/**
	 * 인가 요청을 복원하면서 소비한다. 성공하든 실패하든 쿠키를 먼저 지우고, 행을 찾으면 삭제까지 한다.
	 * 한 번 쓴 인가 요청이 남아 있으면 같은 code_verifier로 재시도할 여지가 생긴다.
	 *
	 * <p>돌아갈 경로는 여기서 request 속성에 옮겨 담는다. 로그인 성공 처리기가 행이 지워진 뒤에
	 * 이 값을 읽어야 하기 때문이다.</p>
	 */
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

	/**
	 * 저장된 행을 Spring Security가 이해하는 인가 요청 객체로 되돌린다.
	 * 여기서 복원한 code_verifier가 곧바로 토큰 교환에 쓰이므로 저장 시점의 값과 정확히 같아야 한다.
	 */
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

	/**
	 * 로그인 후 돌아갈 경로를 검증한다. 통과하지 못하면 조용히 루트로 바꾼다.
	 *
	 * <p>이 값은 쿼리 파라미터라 누구나 채울 수 있고, 검증 없이 저장하면 로그인 직후 외부 주소로 보내는
	 * open redirect가 된다. 우리 도메인처럼 보이는 링크로 사용자를 유인해 로그인시킨 뒤 다른 사이트로
	 * 넘기는 수법에 그대로 쓰인다. 그래서 스킴과 호스트가 없는 절대 경로만 허용하고,
	 * 프로토콜 상대 주소({@code //evil.com})는 슬래시 두 개로 시작하므로 따로 막는다.</p>
	 */
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

	/**
	 * state마다 다른 쿠키 이름을 쓴다. 사용자가 여러 탭에서 동시에 로그인을 시작해도 서로의 쿠키를
	 * 덮어쓰지 않게 하려는 것이다. 이름에 state 원문을 넣지 않고 해시 앞부분을 쓰는 것은
	 * 쿠키 이름이 브라우저와 로그에 그대로 남기 때문이다.
	 */
	private String cookieName(String state) {
		return state == null ? null : COOKIE_PREFIX + TokenHash.sha256(state).substring(0, 16);
	}

	private void clearCookie(HttpServletRequest request, HttpServletResponse response) {
		clearCookie(cookieName(request.getParameter("state")), response);
	}

	private void clearCookie(String name, HttpServletResponse response) {
		if (name != null) addCookie(response, name, "", Duration.ZERO);
	}

	/**
	 * 인가 흐름에 쓰는 쿠키를 공통 속성으로 내보낸다. HttpOnly로 스크립트 접근을 막고,
	 * SameSite=Lax라 공급자에서 돌아오는 최상위 GET 리다이렉트에는 실린다.
	 * 계정 연결 대기 토큰도 같은 속성이 필요해 패키지 안에 열어 두었다.
	 */
	void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(secureCookies)
				.sameSite("Lax")
				.path("/")
				.maxAge(maxAge)
				.build().toString());
	}
}

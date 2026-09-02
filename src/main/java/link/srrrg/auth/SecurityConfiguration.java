package link.srrrg.auth;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.project.ApiKeyService;

/**
 * 세 가지 API 표면이 한 필터 체인 위에서 서로 다른 인증·CSRF 계약을 갖도록 구성한다.
 * 어떤 요청이 어떤 규칙을 받는지가 전부 이 한 곳에서 정해지므로, 경로 하나를 옮기면 인증 정책도 함께 바뀐다.
 *
 * <p>표면별 계약은 다음과 같다. 쿠키 JWT를 쓰는 {@code /api/web/**}는 인증과 CSRF를 모두 적용한다.
 * API 키를 쓰는 {@code /api/v1/**}는 여기서는 {@code permitAll}로 두고 인증을 필터가 직접 처리하며,
 * 브라우저 폼이 아니라 서버 간 호출이므로 CSRF에서 제외한다. secret key로 접근하는 {@code /api/links/**}도
 * 같은 이유로 CSRF에서 빠지고 인증은 컨트롤러가 헤더로 확인한다.</p>
 *
 * <p>{@code /api/v1/**}에 {@code permitAll}을 준 것은 인증을 하지 않는다는 뜻이 아니다.
 * {@code ApiKeyAuthenticationFilter}가 그 경로의 모든 요청을 가로채 키가 없으면 체인을 진행시키지 않고
 * 401을 직접 쓴다. 인증 실패 응답이 웹용 JSON이 아니라 RFC 7807 형식이어야 하기 때문에
 * Spring Security의 기본 진입점 대신 필터에서 끝내는 구조다.</p>
 *
 * <p>세션은 STATELESS다. 서버가 세션을 만들지 않으므로 파드가 늘어나도 sticky session이 필요 없고,
 * 요청 상태를 남기는 requestCache도 끈다.</p>
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration {
	private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

	/**
	 * 필터 체인 하나로 세 표면을 모두 처리한다. 아래 설정의 순서와 위치가 곧 정책이므로,
	 * 각 블록이 어떤 표면을 위한 것인지 확인하지 않고 옮기면 다른 표면의 계약이 깨진다.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ClientRegistrationRepository registrations,
			DatabaseAuthorizationRequestRepository authorizationRequests,
			ProviderOAuth2UserService oauth2UserService,
			ProviderOidcUserService oidcUserService,
			OAuthLoginSuccessHandler successHandler,
			OAuthLoginFailureHandler failureHandler,
			@Value("${srrrg.base-url}") String baseUrl,
			JwtService jwtService,
			ApiKeyService apiKeyService,
			RateLimitService rateLimitService) throws Exception {
		JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
		ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter(apiKeyService, rateLimitService);
		// CSRF 토큰 쿠키만 HttpOnly를 끈다. 화면 스크립트가 값을 읽어 X-XSRF-TOKEN 헤더로 되돌려 보내야
		// double submit 방식이 성립하기 때문이다. 세션 토큰 쿠키는 반대로 HttpOnly를 유지한다.
		CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrf.setHeaderName("X-XSRF-TOKEN");
		// secure 속성을 base URL 스킴에서 끌어온다. 로컬 http 개발에서 켜면 브라우저가 쿠키를 저장하지 않아
		// 모든 쓰기 요청이 CSRF 실패로 막힌다.
		csrf.setCookieCustomizer(cookie -> cookie.secure(baseUrl.startsWith("https://")).sameSite("Lax").path("/"));
		DefaultOAuth2AuthorizationRequestResolver resolver =
				new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
		// PKCE를 켠다. 인가 코드가 리다이렉트 과정에서 가로채여도 code_verifier 없이는 토큰으로 교환할 수 없다.
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

		http
				// 서버 세션을 만들지 않는다. 인증 상태는 매 요청 쿠키의 JWT에서 새로 만들어진다.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
				.csrf(configurer -> configurer
						.spa()
						.csrfTokenRepository(csrf)
						// 이 두 표면은 브라우저 폼이 아니라 헤더 자격증명으로 호출된다. 쿠키가 자동으로 실리지 않으므로
						// CSRF 공격이 성립하지 않고, 서버 간 호출 클라이언트에 토큰 왕복을 요구할 수도 없다.
						.ignoringRequestMatchers("/api/links/**", "/api/v1/**"))
				.authorizeHttpRequests(authorize -> authorize
						// 로그인·토큰 갱신과 초대 수락은 아직 세션이 없는 상태에서 호출되므로 인증을 요구할 수 없다.
						// 각 엔드포인트가 쿠키의 refresh token이나 초대 토큰으로 직접 자격을 확인한다.
						.requestMatchers("/api/web/auth/**", "/invitations/**").permitAll()
						// 인증을 면제하는 것이 아니라 ApiKeyAuthenticationFilter에 넘긴다는 뜻이다.
						// 그 필터가 키를 확인하지 못하면 체인을 더 진행시키지 않고 RFC 7807 응답으로 끝낸다.
						.requestMatchers("/api/v1/**").permitAll()
						.requestMatchers("/api/web/**").authenticated()
						// 나머지는 화면과 공개 리다이렉트라 열어 둔다. 새 관리용 엔드포인트를 만들 때
						// /api/web 아래에 두지 않으면 이 규칙에 걸려 인증 없이 노출된다.
						.anyRequest().permitAll())
				.oauth2Login(oauth -> oauth
						.authorizationEndpoint(endpoint -> endpoint
								.authorizationRequestResolver(resolver)
								.authorizationRequestRepository(authorizationRequests))
						.userInfoEndpoint(userInfo -> userInfo
								.userService(oauth2UserService)
								.oidcUserService(oidcUserService))
						.successHandler(successHandler)
						.failureHandler(failureHandler))
				.exceptionHandling(exceptions -> exceptions
							// 웹 표면의 인증 실패는 로그인 페이지로 보내지 않고 JSON으로 답한다.
							// 화면이 fetch로 호출하므로 리다이렉트를 받으면 로그인 HTML을 데이터로 파싱하게 된다.
						.authenticationEntryPoint((request, response, exception) -> {
							log.warn("Unauthenticated web request: method={}, reason={}", request.getMethod(), exception.getClass().getSimpleName());
								writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
										"AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
						})
						.accessDeniedHandler((request, response, exception) -> {
							log.warn("Denied web request: method={}, reason={}", request.getMethod(), exception.getClass().getSimpleName());
								writeError(response, HttpServletResponse.SC_FORBIDDEN,
										"ACCESS_DENIED", "요청이 허용되지 않았습니다.");
						}))
				// 인가 판정 전에 JWT를 읽어 SecurityContext를 채운다. 그 뒤 API 키 필터가 v1 경로만 처리하고,
				// CsrfCookieFilter는 CsrfFilter가 토큰을 준비한 뒤에 실행돼야 하므로 그 다음이다.
				.addFilterBefore(jwtFilter, AuthorizationFilter.class)
				.addFilterAfter(apiKeyFilter, JwtAuthenticationFilter.class)
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

		return http.build();
	}

	/**
	 * 인증·인가 실패 응답을 컨트롤러에 도달하기 전에 직접 쓴다. 이 시점에는 예외 처리기가 동작하지 않으므로
	 * {@code ApiErrorResponse}와 같은 모양의 JSON을 손으로 만든다.
	 * 문자열을 직접 조립하므로 여기에 넘기는 code와 message는 코드에 적힌 고정 문구만 사용한다.
	 */
	private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}

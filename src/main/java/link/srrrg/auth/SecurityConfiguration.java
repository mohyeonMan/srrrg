package link.srrrg.auth;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import link.srrrg.project.ApiKeyService;

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
	private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ClientRegistrationRepository registrations,
			DatabaseAuthorizationRequestRepository authorizationRequests,
			ProviderOAuth2UserService oauth2UserService,
			ProviderOidcUserService oidcUserService,
			OAuthLoginSuccessHandler successHandler,
			OAuthLoginFailureHandler failureHandler,
			JwtService jwtService,
			ApiKeyService apiKeyService) throws Exception {
		JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
		ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter(apiKeyService);
		CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrf.setHeaderName("X-XSRF-TOKEN");
		csrf.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax").path("/"));
		DefaultOAuth2AuthorizationRequestResolver resolver =
				new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

		http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.csrf(configurer -> configurer
						.spa()
						.csrfTokenRepository(csrf)
						.ignoringRequestMatchers("/api/links/**", "/api/v1/**"))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/web/auth/**", "/invitations/**").permitAll()
						.requestMatchers("/api/v1/**").permitAll()
						.requestMatchers("/api/web/**").authenticated()
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
				.addFilterBefore(jwtFilter, AuthorizationFilter.class)
				.addFilterAfter(apiKeyFilter, JwtAuthenticationFilter.class)
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

		return http.build();
	}

	private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}

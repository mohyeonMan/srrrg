package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

import link.srrrg.common.util.SecureRandomStringGenerator;

class DatabaseAuthorizationRequestRepositoryTest {

	@Test
	void storesPkceServerSideAndRejectsExternalReturnPath() {
		OAuthAuthorizationRequestRepository persistence = mock(OAuthAuthorizationRequestRepository.class);
		SecureRandomStringGenerator random = mock(SecureRandomStringGenerator.class);
		when(random.generate(any(), eq(43))).thenReturn("a".repeat(43));
		DatabaseAuthorizationRequestRepository repository =
				new DatabaseAuthorizationRequestRepository(persistence, random);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("returnTo", "https://evil.example/path");
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthorizationRequest authorization = OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri("https://provider.example/authorize")
				.clientId("client")
				.redirectUri("https://srrrg.link/login/oauth2/code/google")
				.scopes(java.util.Set.of("openid", "email"))
				.state("state")
				.attributes(attributes -> {
					attributes.put("registration_id", "google");
					attributes.put(PkceParameterNames.CODE_VERIFIER, "verifier");
				})
				.additionalParameters(parameters ->
						parameters.put(PkceParameterNames.CODE_CHALLENGE, "challenge"))
				.build();

		repository.saveAuthorizationRequest(authorization, request, response);

		ArgumentCaptor<OAuthAuthorizationRequest> saved = ArgumentCaptor.forClass(OAuthAuthorizationRequest.class);
		verify(persistence).save(saved.capture());
		assertThat(saved.getValue().getCodeVerifier()).isEqualTo("verifier");
		assertThat(saved.getValue().getReturnPath()).isEqualTo("/");
		assertThat(saved.getValue().getTokenHash()).doesNotContain("a".repeat(43));
		assertThat(response.getHeaders("Set-Cookie").getFirst())
				.contains("srrrg_oauth_request_", "HttpOnly", "Secure", "SameSite=Lax");

		MockHttpServletResponse secondResponse = new MockHttpServletResponse();
		repository.saveAuthorizationRequest(
				OAuth2AuthorizationRequest.from(authorization).state("another-state").build(), request, secondResponse);
		assertThat(secondResponse.getHeaders("Set-Cookie").getFirst())
				.isNotEqualTo(response.getHeaders("Set-Cookie").getFirst());
	}
}

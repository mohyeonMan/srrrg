package link.srrrg.auth.request.service;

import link.srrrg.auth.request.service.ApiKeyRequestAuthorizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import link.srrrg.web.error.model.PublicApiException;
import link.srrrg.project.apikey.model.ApiKeyPrincipal;
import link.srrrg.project.apikey.model.ApiKeyScope;

class ApiKeyRequestAuthorizerTest {
	private final ApiKeyRequestAuthorizer authorizer = new ApiKeyRequestAuthorizer();

	@Test
	void requiresAuthenticatedPrincipalMatchingProjectAndScope() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		ApiKeyPrincipal principal = new ApiKeyPrincipal(1L, 7L, Set.of(ApiKeyScope.STATS_READ));
		request.setAttribute("srrrg.apiKeyPrincipal", principal);

		assertSame(principal, authorizer.require(request, 7L, ApiKeyScope.STATS_READ));
	}

	@Test
	void rejectsMissingPrincipalProjectMismatchAndMissingScopeWithStableCodes() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		assertCode("API_KEY_INVALID",
				assertThrows(PublicApiException.class, () -> authorizer.requireAuthenticated(request)));

		request.setAttribute("srrrg.apiKeyPrincipal",
				new ApiKeyPrincipal(1L, 7L, Set.of(ApiKeyScope.LINKS_READ)));
		assertCode("PROJECT_ACCESS_DENIED",
				assertThrows(PublicApiException.class,
						() -> authorizer.require(request, 8L, ApiKeyScope.LINKS_READ)));
		assertCode("SCOPE_REQUIRED",
				assertThrows(PublicApiException.class,
						() -> authorizer.require(request, 7L, ApiKeyScope.STATS_READ)));
	}

	private void assertCode(String expected, PublicApiException exception) {
		assertEquals(expected, exception.code());
	}
}

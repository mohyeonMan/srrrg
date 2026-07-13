package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import link.srrrg.link.dto.RedirectLink;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;

class RedirectControllerTest {

	@Test
	void validCodeReturnsSecureRedirectPageInsteadOf302() {
		LinkService service = mock(LinkService.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		Model model = mock(Model.class);
		ClientRequestInfoResolver resolver = mock(ClientRequestInfoResolver.class);
		ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "agent");
		when(resolver.resolve(request)).thenReturn(requestInfo);
		when(service.resolveRedirectPage("aB3x9Q", requestInfo))
				.thenReturn(new RedirectLink("aB3x9Q", "https://example.com/path?q=1", null, null));
		when(request.getRequestURL()).thenReturn(new StringBuffer("https://srrrg.link/aB3x9Q"));

		String view = new RedirectController(service, resolver).redirect("aB3x9Q", request, response, model);

		assertThat(view).isEqualTo("redirect-confirm");
		verify(response).setHeader("Cache-Control", "no-store");
		verify(response).setHeader("Content-Security-Policy",
				"default-src 'self'; script-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none';");
		verify(model).addAttribute("originalUrlHost", "example.com");
		verify(model).addAttribute("checkUrl", "/api/redirect-check/aB3x9Q");
	}

	@Test
	void exposesFreshCachedResultToThePage() {
		LinkService service = mock(LinkService.class);
		ClientRequestInfoResolver resolver = mock(ClientRequestInfoResolver.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		Model model = mock(Model.class);
		ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "agent");
		when(resolver.resolve(request)).thenReturn(requestInfo);
		when(service.resolveRedirectPage("aB3x9Q", requestInfo)).thenReturn(
				new RedirectLink("aB3x9Q", "https://example.com/path", LinkStatus.NO_THREAT_FOUND,
						"/api/redirect/aB3x9Q?ticket=ticket"));
		when(request.getRequestURL()).thenReturn(new StringBuffer("https://srrrg.link/aB3x9Q"));

		new RedirectController(service, resolver).redirect("aB3x9Q", request, response, model);

		verify(model).addAttribute("cachedStatus", LinkStatus.NO_THREAT_FOUND);
		verify(model).addAttribute("cachedRedirectUrl", "/api/redirect/aB3x9Q?ticket=ticket");
	}
}

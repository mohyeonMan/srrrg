package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;
import link.srrrg.link.dto.RedirectLink;

class RedirectControllerTest {

	@Test
	void returnsFoundWithOriginalUrlLocation() {
		LinkService linkService = mock(LinkService.class);
		ClientRequestInfoResolver requestInfoResolver = mock(ClientRequestInfoResolver.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		Model model = mock(Model.class);
		ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "test-agent");
		when(requestInfoResolver.resolve(request)).thenReturn(requestInfo);
		when(linkService.resolveRedirect("aB3x9Q", requestInfo))
				.thenReturn(new RedirectLink("aB3x9Q", "https://example.com/path", true));
		RedirectController controller = new RedirectController(linkService, requestInfoResolver);

		Object result = controller.redirect("aB3x9Q", request, model);

		assertThat(result).isInstanceOf(ResponseEntity.class);
		ResponseEntity<?> response = (ResponseEntity<?>) result;
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getLocation()).hasToString("https://example.com/path");
	}

	@Test
	void returnsConfirmPageWhenLinkIsNotTrusted() {
		LinkService linkService = mock(LinkService.class);
		ClientRequestInfoResolver requestInfoResolver = mock(ClientRequestInfoResolver.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		Model model = mock(Model.class);
		ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "test-agent");
		when(requestInfoResolver.resolve(request)).thenReturn(requestInfo);
		when(request.getRequestURL()).thenReturn(new StringBuffer("https://srrrg.link/aB3x9Q"));
		when(linkService.resolveRedirect("aB3x9Q", requestInfo))
				.thenReturn(new RedirectLink("aB3x9Q", "https://example.com/path", false));
		RedirectController controller = new RedirectController(linkService, requestInfoResolver);

		Object result = controller.redirect("aB3x9Q", request, model);

		assertThat(result).isEqualTo("redirect-confirm");
	}

	@Test
	void confirmsRedirectWithPost() {
		LinkService linkService = mock(LinkService.class);
		ClientRequestInfoResolver requestInfoResolver = mock(ClientRequestInfoResolver.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "test-agent");
		when(requestInfoResolver.resolve(request)).thenReturn(requestInfo);
		when(linkService.confirmRedirect("aB3x9Q", requestInfo))
				.thenReturn(new RedirectLink("aB3x9Q", "https://example.com/path", false));
		RedirectController controller = new RedirectController(linkService, requestInfoResolver);

		ResponseEntity<Void> response = controller.confirmRedirect("aB3x9Q", request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getLocation()).hasToString("https://example.com/path");
	}
}

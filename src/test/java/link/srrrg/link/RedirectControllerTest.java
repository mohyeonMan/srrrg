package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.ClientRequestInfoResolver;

class RedirectControllerTest {

	@Test
	void returnsFoundWithOriginalUrlLocation() {
		LinkService linkService = mock(LinkService.class);
		ClientRequestInfoResolver requestInfoResolver = mock(ClientRequestInfoResolver.class);
		HttpServletRequest request = mock(HttpServletRequest.class);
		ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "test-agent");
		when(requestInfoResolver.resolve(request)).thenReturn(requestInfo);
		when(linkService.resolveRedirect("aB3x9Q", requestInfo)).thenReturn("https://example.com/path");
		RedirectController controller = new RedirectController(linkService, requestInfoResolver);

		ResponseEntity<Void> response = controller.redirect("aB3x9Q", request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getLocation()).hasToString("https://example.com/path");
	}
}

package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RedirectControllerTest {

	@Test
	void returnsFoundWithOriginalUrlLocation() {
		LinkService linkService = mock(LinkService.class);
		when(linkService.resolveRedirect("aB3x9Q")).thenReturn("https://example.com/path");
		RedirectController controller = new RedirectController(linkService);

		ResponseEntity<Void> response = controller.redirect("aB3x9Q");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getLocation()).hasToString("https://example.com/path");
	}
}

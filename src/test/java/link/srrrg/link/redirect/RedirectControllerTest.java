package link.srrrg.link.redirect;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import link.srrrg.link.LinkGoneException;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.access.ClientRequestInfoResolver;

class RedirectControllerTest {

	private final RedirectService service = mock(RedirectService.class);
	private final ClientRequestInfoResolver resolver = mock(ClientRequestInfoResolver.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new RedirectController(service, resolver))
				.setControllerAdvice(new RedirectExceptionHandler())
				.build();
	}

	@Test
	void safeUrlRedirectsDirectlyToTheOriginalUrl() throws Exception {
		when(service.redirect(eq("aB3x9Q"), any())).thenReturn("https://example.com/path?q=1");

		mvc.perform(get("/aB3x9Q"))
				.andExpect(status().isFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("Location", "https://example.com/path?q=1"));
	}

	@Test
	void threatUrlReturnsForbiddenPage() throws Exception {
		when(service.redirect(eq("aB3x9Q"), any())).thenThrow(new UnsafeUrlException());

		mvc.perform(get("/aB3x9Q"))
				.andExpect(status().isForbidden())
				.andExpect(view().name("redirect-error"))
					.andExpect(model().attribute("status", 403))
					.andExpect(model().attribute("statusKind", "blocked"))
				.andExpect(model().attribute("title", "잠재적으로 위험한 링크입니다"))
				.andExpect(model().attribute("safeBrowsingAdvisory", true));
	}

	@Test
	void failedVerificationReturnsRetryableServiceUnavailablePage() throws Exception {
		when(service.redirect(eq("aB3x9Q"), any())).thenThrow(new UrlRiskCheckFailedException());

		mvc.perform(get("/aB3x9Q"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Retry-After", "30"))
				.andExpect(view().name("redirect-error"))
					.andExpect(model().attribute("status", 503))
					.andExpect(model().attribute("retryAfterSeconds", 30))
					.andExpect(model().attribute("statusKind", "retryable"));
	}

	@Test
	void expiredLinkReturnsDistinctGonePage() throws Exception {
		when(service.redirect(eq("aB3x9Q"), any()))
				.thenThrow(new LinkGoneException(LinkGoneException.Reason.EXPIRED));

		mvc.perform(get("/aB3x9Q"))
				.andExpect(status().isGone())
				.andExpect(view().name("redirect-error"))
				.andExpect(model().attribute("title", "만료된 링크입니다"))
				.andExpect(model().attribute("statusKind", "gone"));
	}
}

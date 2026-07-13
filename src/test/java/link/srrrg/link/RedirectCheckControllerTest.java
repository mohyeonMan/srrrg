package link.srrrg.link;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import link.srrrg.link.access.ClientRequestInfoResolver;
import link.srrrg.link.dto.RedirectCheckResponse;
import link.srrrg.link.risk.UrlRiskCheckResult;

class RedirectCheckControllerTest {
	private final LinkService service = mock(LinkService.class);
	private final ClientRequestInfoResolver resolver = mock(ClientRequestInfoResolver.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new RedirectCheckController(service, resolver)).build();
	}

	@Test
	void returnsMinimalNoStoreSuccessResponse() throws Exception {
		when(service.checkRedirect(org.mockito.ArgumentMatchers.eq("aB3x9Q"), any()))
				.thenReturn(new RedirectCheckResponse(UrlRiskCheckResult.NO_THREAT_FOUND,
						"/api/redirect/aB3x9Q?ticket=ticket"));
		mvc.perform(post("/api/redirect-check/aB3x9Q"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.status").value("NO_THREAT_FOUND"))
				.andExpect(jsonPath("$.redirectUrl").value("/api/redirect/aB3x9Q?ticket=ticket"));
	}

	@Test
	void threatResponseDoesNotExposeRedirectUrl() throws Exception {
		when(service.checkRedirect(org.mockito.ArgumentMatchers.eq("aB3x9Q"), any()))
				.thenReturn(new RedirectCheckResponse(UrlRiskCheckResult.THREAT_DETECTED, null));
		mvc.perform(post("/api/redirect-check/aB3x9Q"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("THREAT_DETECTED"))
				.andExpect(jsonPath("$.redirectUrl").doesNotExist());
	}

	@Test
	void redirectEndpointIssuesFoundResponseThroughServer() throws Exception {
		when(service.redirectToOriginal(org.mockito.ArgumentMatchers.eq("aB3x9Q"),
				org.mockito.ArgumentMatchers.eq("ticket"), any()))
				.thenReturn("https://example.com/path");

		mvc.perform(get("/api/redirect/aB3x9Q").param("ticket", "ticket"))
				.andExpect(status().isFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("Location", "https://example.com/path"));
	}
}

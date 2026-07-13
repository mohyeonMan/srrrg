package link.srrrg.link;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
				.thenReturn(new RedirectCheckResponse(UrlRiskCheckResult.NO_THREAT_FOUND, "https://example.com/path"));
		mvc.perform(post("/api/redirect-check/aB3x9Q"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.status").value("NO_THREAT_FOUND"))
				.andExpect(jsonPath("$.redirectUrl").value("https://example.com/path"));
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
}

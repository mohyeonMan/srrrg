package link.srrrg.link.management;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import link.srrrg.common.GlobalExceptionHandler;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.management.dto.CreateLinkResponse;
import link.srrrg.link.management.dto.DeleteLinkResponse;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.LinkStatisticsSummary;
import link.srrrg.link.management.dto.UpdateLinkRequest;

class LinkControllerTest {

	private static final String SECRET_KEY_HEADER = "X-Srrrg-Secret-Key";

	private final LinkManagementService linkService = mock(LinkManagementService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new LinkController(linkService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void createsLinkWithExistingContract() throws Exception {
		when(linkService.create(any())).thenReturn(new CreateLinkResponse(
				"aB3x9Q",
				"https://srrrg.link/aB3x9Q",
				"srrrg_sk_secret",
				null
		));

		mockMvc.perform(post("/api/links")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"originalUrl\":\"https://example.com\",\"expiresAt\":null}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("aB3x9Q"))
				.andExpect(jsonPath("$.secretKey").value("srrrg_sk_secret"));
	}

	@Test
	void returnsManagedLinkWithStatistics() throws Exception {
		when(linkService.getManagedLink("aB3x9Q", "srrrg_sk_secret"))
				.thenReturn(managementResponse());

		mockMvc.perform(get("/api/links/aB3x9Q")
					.header(SECRET_KEY_HEADER, "srrrg_sk_secret"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("aB3x9Q"))
				.andExpect(jsonPath("$.statistics.clickCount").value(12))
				.andExpect(jsonPath("$.statistics.redirectCount").value(8))
				.andExpect(jsonPath("$.status").doesNotExist())
				.andExpect(jsonPath("$.verifiedAt").doesNotExist())
				.andExpect(jsonPath("$.secretKey").doesNotExist())
				.andExpect(jsonPath("$.secretKeyHash").doesNotExist());
	}

	@Test
	void returnsBadRequestWhenSecretHeaderIsMissing() throws Exception {
		mockMvc.perform(get("/api/links/aB3x9Q"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsSameNotFoundResponseForAuthenticationFailure() throws Exception {
		when(linkService.getManagedLink("aB3x9Q", "wrong-secret"))
				.thenThrow(new LinkNotFoundException());

		mockMvc.perform(get("/api/links/aB3x9Q")
					.header(SECRET_KEY_HEADER, "wrong-secret"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
	}

	@Test
	void distinguishesExplicitNullExpirationInPatch() throws Exception {
		when(linkService.updateManagedLink(eq("aB3x9Q"), eq("srrrg_sk_secret"), any()))
				.thenReturn(managementResponse());

		mockMvc.perform(patch("/api/links/aB3x9Q")
					.header(SECRET_KEY_HEADER, "srrrg_sk_secret")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"expiresAt\":null}"))
				.andExpect(status().isOk());

		ArgumentCaptor<UpdateLinkRequest> requestCaptor = ArgumentCaptor.forClass(UpdateLinkRequest.class);
		verify(linkService).updateManagedLink(
				eq("aB3x9Q"),
				eq("srrrg_sk_secret"),
				requestCaptor.capture()
		);
		UpdateLinkRequest request = requestCaptor.getValue();
		org.assertj.core.api.Assertions.assertThat(request.isExpiresAtPresent()).isTrue();
		org.assertj.core.api.Assertions.assertThat(request.getExpiresAt()).isNull();
		org.assertj.core.api.Assertions.assertThat(request.isOriginalUrlPresent()).isFalse();
	}

	@Test
	void returnsBadRequestForMalformedPatchBody() throws Exception {
		mockMvc.perform(patch("/api/links/aB3x9Q")
					.header(SECRET_KEY_HEADER, "srrrg_sk_secret")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"expiresAt\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsDeletedResponse() throws Exception {
		when(linkService.deleteManagedLink("aB3x9Q", "srrrg_sk_secret"))
				.thenReturn(new DeleteLinkResponse(true));

		mockMvc.perform(delete("/api/links/aB3x9Q")
					.header(SECRET_KEY_HEADER, "srrrg_sk_secret"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").value(true));
	}

	@Test
	void returnsGoneForDeletedLink() throws Exception {
		when(linkService.getManagedLink("aB3x9Q", "srrrg_sk_secret"))
				.thenThrow(new LinkGoneException());

		mockMvc.perform(get("/api/links/aB3x9Q")
					.header(SECRET_KEY_HEADER, "srrrg_sk_secret"))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("LINK_GONE"));
	}

	private LinkManagementResponse managementResponse() {
		return new LinkManagementResponse(
				"aB3x9Q",
				"https://srrrg.link/aB3x9Q",
				"https://example.com/path",
				null,
				new LinkStatisticsSummary(12, 8),
				Instant.parse("2026-07-10T10:00:00Z"),
				Instant.parse("2026-07-10T11:00:00Z")
		);
	}
}

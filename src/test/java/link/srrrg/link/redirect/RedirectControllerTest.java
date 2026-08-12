package link.srrrg.link.redirect;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import link.srrrg.auth.WebAccountModel;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.access.ClientRequestInfoResolver;

class RedirectControllerTest {

	private final RedirectService service = mock(RedirectService.class);
	private final ClientRequestInfoResolver resolver = mock(ClientRequestInfoResolver.class);
	private final WebAccountModel webAccountModel = mock(WebAccountModel.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new RedirectController(service, resolver))
				.setControllerAdvice(new RedirectExceptionHandler(webAccountModel))
				.build();
	}

	/**
	 * @ControllerAdvice 의 @ModelAttribute 는 예외 핸들러가 렌더하는 뷰에 적용되지 않는다.
	 * 그래서 오류 화면만 로그인 상태에서도 헤더가 로그아웃으로 보였다.
	 * 핸들러가 계정 정보를 직접 채우는지 고정한다.
	 */
	@Test
	void errorPagePopulatesHeaderAccountStateBecauseModelAttributeAdviceDoesNotRunHere() throws Exception {
		// 코드는 정확히 6자여야 매핑에 걸린다(@GetMapping("/{code:[0-9A-Za-z]{6}}")).
		when(service.redirect(eq("srrrg.link"), eq("zzzzzz"), any()))
				.thenThrow(new LinkNotFoundException());

		mvc.perform(get("/zzzzzz").header("Host", "srrrg.link"))
				.andExpect(status().isNotFound())
				.andExpect(view().name("redirect-error"));

		verify(webAccountModel).apply(any(), any());
	}

	@Test
	void safeUrlRedirectsDirectlyToTheOriginalUrl() throws Exception {
		when(service.redirect(eq("srrrg.link"), eq("aB3x9Q"), any())).thenReturn("https://example.com/path?q=1");

		mvc.perform(get("/aB3x9Q").header("Host", "srrrg.link"))
				.andExpect(status().isFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("Location", "https://example.com/path?q=1"));
	}

	@Test
	void usesHostHeaderInsteadOfForwardedHost() throws Exception {
		when(service.redirect(eq("srrrg.link"), eq("aB3x9Q"), any())).thenReturn("https://example.com");

		mvc.perform(get("/aB3x9Q")
				.header("Host", "srrrg.link")
				.header("X-Forwarded-Host", "attacker.example"))
				.andExpect(status().isFound());

		verify(service).redirect(eq("srrrg.link"), eq("aB3x9Q"), any());
	}

	@Test
	void threatUrlReturnsForbiddenPage() throws Exception {
		when(service.redirect(eq("srrrg.link"), eq("aB3x9Q"), any())).thenThrow(new UnsafeUrlException());

		mvc.perform(get("/aB3x9Q").header("Host", "srrrg.link"))
				.andExpect(status().isForbidden())
				.andExpect(view().name("redirect-error"))
					.andExpect(model().attribute("status", 403))
					.andExpect(model().attribute("statusKind", "blocked"))
				.andExpect(model().attribute("title", "잠재적으로 위험한 링크입니다"))
				.andExpect(model().attribute("safeBrowsingAdvisory", true));
	}

	@Test
	void failedVerificationReturnsRetryableServiceUnavailablePage() throws Exception {
		when(service.redirect(eq("srrrg.link"), eq("aB3x9Q"), any())).thenThrow(new UrlRiskCheckFailedException());

		mvc.perform(get("/aB3x9Q").header("Host", "srrrg.link"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Retry-After", "30"))
				.andExpect(view().name("redirect-error"))
					.andExpect(model().attribute("status", 503))
					.andExpect(model().attribute("retryAfterSeconds", 30))
					.andExpect(model().attribute("statusKind", "retryable"));
	}

	@Test
	void expiredLinkReturnsDistinctGonePage() throws Exception {
		when(service.redirect(eq("srrrg.link"), eq("aB3x9Q"), any()))
				.thenThrow(new LinkGoneException(LinkGoneException.Reason.EXPIRED));

		mvc.perform(get("/aB3x9Q").header("Host", "srrrg.link"))
				.andExpect(status().isGone())
				.andExpect(view().name("redirect-error"))
				.andExpect(model().attribute("title", "만료된 링크입니다"))
				.andExpect(model().attribute("statusKind", "gone"));
	}
}

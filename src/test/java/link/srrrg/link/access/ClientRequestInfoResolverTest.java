package link.srrrg.link.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

class ClientRequestInfoResolverTest {

	@Test
	void usesRemoteAddressWhenForwardedHeadersAreNotTrusted() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRemoteAddr()).thenReturn("203.0.113.10");
		when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1");
		when(request.getHeader("Referer")).thenReturn("https://referrer.example");
		when(request.getHeader("User-Agent")).thenReturn("test-agent");

		ClientRequestInfo info = new ClientRequestInfoResolver(false).resolve(request);

		assertThat(info.ipAddress()).isEqualTo("203.0.113.10");
		assertThat(info.referer()).isEqualTo("https://referrer.example");
		assertThat(info.userAgent()).isEqualTo("test-agent");
	}

	@Test
	void usesFirstForwardedAddressWhenConfigured() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1, 10.0.0.2");

		ClientRequestInfo info = new ClientRequestInfoResolver(true).resolve(request);

		assertThat(info.ipAddress()).isEqualTo("198.51.100.1");
	}
}

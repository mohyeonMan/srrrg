package link.srrrg.link.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import link.srrrg.link.Link;
import link.srrrg.link.access.LinkAccessEvent.Outcome;

class LinkAccessEventRecorderTest {

	@Test
	void enrichesAndStoresAccessEvent() {
		LinkAccessEventRepository repository = mock(LinkAccessEventRepository.class);
		UserAgentParser userAgentParser = mock(UserAgentParser.class);
		Link link = mock(Link.class);
		Instant accessedAt = Instant.parse("2026-07-30T10:00:00Z");
		ClientRequestInfo requestInfo = new ClientRequestInfo(
				"203.0.113.10",
				"https://referrer.example",
				"test-agent"
		);
		when(userAgentParser.parse("test-agent"))
				.thenReturn(new UserAgentInfo("Other", null, "Other", null, "DESKTOP", false));
		LinkAccessEventRecorder recorder = new LinkAccessEventRecorder(repository, userAgentParser);

		recorder.record(link, accessedAt, Outcome.REDIRECTED, requestInfo, Map.of("utm_source", "google"));

		ArgumentCaptor<LinkAccessEvent> eventCaptor = ArgumentCaptor.forClass(LinkAccessEvent.class);
		verify(repository).save(eventCaptor.capture());
		assertThat(eventCaptor.getValue().getLink()).isSameAs(link);
		assertThat(eventCaptor.getValue().getAccessedAt()).isEqualTo(accessedAt);
		assertThat(eventCaptor.getValue().getOutcome()).isEqualTo(Outcome.REDIRECTED);
		assertThat(eventCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.10");
		assertThat(eventCaptor.getValue().getEffectiveUtm()).containsEntry("utm_source", "google");
	}
}

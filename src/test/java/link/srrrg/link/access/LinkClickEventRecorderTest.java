package link.srrrg.link.access;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import link.srrrg.link.Link;

class LinkClickEventRecorderTest {

	@Test
	void enrichesAndStoresClickEvent() {
		LinkClickEventRepository repository = mock(LinkClickEventRepository.class);
		UserAgentParser userAgentParser = mock(UserAgentParser.class);
		Link link = mock(Link.class);
		ClientRequestInfo requestInfo = new ClientRequestInfo(
				"203.0.113.10",
				"https://referrer.example",
				"test-agent"
		);
		when(userAgentParser.parse("test-agent"))
				.thenReturn(new UserAgentInfo("Other", null, "Other", null, "DESKTOP", false));
		LinkClickEventRecorder recorder = new LinkClickEventRecorder(repository, userAgentParser);

		recorder.record(link, requestInfo);

		verify(repository).save(any(LinkClickEvent.class));
	}
}

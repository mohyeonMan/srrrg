package link.srrrg.link.access;

import java.time.Instant;

import org.springframework.stereotype.Component;

import link.srrrg.link.Link;
import link.srrrg.link.access.LinkAccessEvent.Outcome;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LinkAccessEventRecorder {

	private final LinkAccessEventRepository repository;
	private final UserAgentParser userAgentParser;

	public void record(Link link, Instant accessedAt, Outcome outcome, ClientRequestInfo requestInfo) {
		UserAgentInfo userAgentInfo = userAgentParser.parse(requestInfo.userAgent());
		repository.save(LinkAccessEvent.create(link, accessedAt, outcome, requestInfo, userAgentInfo));
	}
}

package link.srrrg.link.access;

import org.springframework.stereotype.Component;

import link.srrrg.link.Link;

@Component
public class LinkAccessEventRecorder {

	private final LinkAccessEventRepository repository;
	private final UserAgentParser userAgentParser;

	public LinkAccessEventRecorder(
			LinkAccessEventRepository repository,
			UserAgentParser userAgentParser
	) {
		this.repository = repository;
		this.userAgentParser = userAgentParser;
	}

	public void record(Link link, ClientRequestInfo requestInfo) {
		UserAgentInfo userAgentInfo = userAgentParser.parse(requestInfo.userAgent());
		repository.save(LinkAccessEvent.create(link, requestInfo, userAgentInfo));
	}
}

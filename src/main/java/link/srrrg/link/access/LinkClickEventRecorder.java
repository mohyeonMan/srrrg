package link.srrrg.link.access;

import org.springframework.stereotype.Component;

import link.srrrg.link.Link;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LinkClickEventRecorder {

	private final LinkClickEventRepository repository;
	private final UserAgentParser userAgentParser;

	public void record(Link link, ClientRequestInfo requestInfo) {
		UserAgentInfo userAgentInfo = userAgentParser.parse(requestInfo.userAgent());
		repository.save(LinkClickEvent.create(link, requestInfo, userAgentInfo));
	}
}

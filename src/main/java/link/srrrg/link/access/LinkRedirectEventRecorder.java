package link.srrrg.link.access;

import org.springframework.stereotype.Component;

import link.srrrg.link.Link;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LinkRedirectEventRecorder {

	private final LinkRedirectEventRepository repository;
	private final UserAgentParser userAgentParser;

	public void record(Link link, ClientRequestInfo requestInfo) {
		UserAgentInfo userAgentInfo = userAgentParser.parse(requestInfo.userAgent());
		repository.save(LinkRedirectEvent.create(link, requestInfo, userAgentInfo));
	}
}

package link.srrrg.link.access;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import link.srrrg.link.Link;
import link.srrrg.link.access.LinkAccessEvent.Outcome;
import lombok.RequiredArgsConstructor;

/**
 * 접근 이벤트를 저장한다. User-Agent 해석을 기록 시점에 한 번만 수행해 그 결과를 컬럼으로 남기므로,
 * 통계 조회는 원문을 다시 파싱하지 않는다.
 *
 * <p>트랜잭션 경계는 여기 없다. 호출자가 이벤트를 리다이렉트와 함께 커밋할지, 별도 트랜잭션으로
 * 먼저 커밋할지 정한다. 차단이나 검사 실패처럼 예외로 끝나는 경로에서는 함께 롤백되면 기록이 사라지기 때문이다.</p>
 */
@Component
@RequiredArgsConstructor
public class LinkAccessEventRecorder {

	private final LinkAccessEventRepository repository;
	private final UserAgentParser userAgentParser;

	public void record(Link link, Instant accessedAt, Outcome outcome, ClientRequestInfo requestInfo) {
		record(link, accessedAt, outcome, requestInfo, Map.of());
	}

	public void record(Link link, Instant accessedAt, Outcome outcome, ClientRequestInfo requestInfo,
			Map<String, String> effectiveUtm) {
		UserAgentInfo userAgentInfo = userAgentParser.parse(requestInfo.userAgent());
		repository.save(LinkAccessEvent.create(link, accessedAt, outcome, requestInfo, userAgentInfo, effectiveUtm));
	}
}

package link.srrrg.link.access;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.link.Link;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkStatus;
import link.srrrg.link.risk.UrlRiskCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedirectCheckRecorder {

	private final LinkRepository linkRepository;
	private final LinkClickEventRecorder clickEventRecorder;
	private final LinkRedirectEventRecorder redirectEventRecorder;

	@Transactional
	public boolean recordCheck(Link link, String checkedUrl, UrlRiskCheckResult result,
			ClientRequestInfo requestInfo) {
		// URL 조건이 포함된 벌크 업데이트로 다른 URL의 검사 결과가 덮이지 않게 함.
		int verificationUpdates = linkRepository.updateVerificationByCodeAndOriginalUrl(
				link.getCode(), checkedUrl, LinkStatus.from(result), Instant.now());
		if (verificationUpdates != 1) {
			log.warn("Redirect verification result not recorded: reason=URL_CHANGED, code={}", link.getCode());
			return false;
		}

		// 실제 이동이 결정되는 위협 미탐지 결과만 접근 통계에 포함함.
		if (result == UrlRiskCheckResult.NO_THREAT_FOUND) {
			recordAccess(link, requestInfo);
		}
		log.debug("Redirect verification result recorded: code={}, result={}", link.getCode(), result);
		return true;
	}

	@Transactional
	public Optional<LinkStatus> reuseCachedCheck(String code, String originalUrl, LinkStatus status,
			Instant verifiedAt, ClientRequestInfo requestInfo) {
		// 페이지 조회와 캐시 사용 사이에 링크가 바뀌지 않았는지 트랜잭션 안에서 다시 확인함.
		Link link = linkRepository.findByCode(code).orElse(null);
		if (!matchesCachedVerification(link, originalUrl, status, verifiedAt)) {
			log.debug("Cached redirect verification not reused: reason=STALE_DATA, code={}", code);
			return Optional.empty();
		}
		// 캐시된 성공 결과는 검사 API가 없으므로 페이지 조회 시 접근 통계를 기록함.
		if (status == LinkStatus.NO_THREAT_FOUND) {
			recordAccess(link, requestInfo);
		}
		log.info("Cached redirect verification reused: code={}, status={}, verifiedAt={}", code, status, verifiedAt);
		return Optional.of(status);
	}

	private boolean matchesCurrentUrl(Link link, String checkedUrl) {
		return link != null && !link.isDeleted() && !link.isExpiredAt(Instant.now())
				&& checkedUrl.equals(link.getOriginalUrl());
	}

	private boolean matchesCachedVerification(Link link, String originalUrl, LinkStatus status, Instant verifiedAt) {
		// URL, 상태, 검사 시각이 모두 같아야 동일한 캐시 결과로 판단함.
		return matchesCurrentUrl(link, originalUrl)
				&& status == link.getStatus()
				&& verifiedAt.equals(link.getVerifiedAt());
	}

	private void recordAccess(Link link, ClientRequestInfo requestInfo) {
		clickEventRecorder.record(link, requestInfo);
		redirectEventRecorder.record(link, requestInfo);
		int clickUpdates = linkRepository.incrementClickCountByCode(link.getCode());
		int redirectUpdates = linkRepository.incrementRedirectCountByCode(link.getCode());
		if (clickUpdates != 1 || redirectUpdates != 1) {
			log.warn("Redirect statistics update failed: code={}, clickUpdates={}, redirectUpdates={}",
					link.getCode(), clickUpdates, redirectUpdates);
			throw new LinkNotFoundException();
		}
	}
}

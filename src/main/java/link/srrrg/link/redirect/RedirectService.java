package link.srrrg.link.redirect;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import link.srrrg.link.Link;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkStatus;
import link.srrrg.link.UrlValidator;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.LinkClickEventRecorder;
import link.srrrg.link.access.LinkRedirectEventRecorder;
import link.srrrg.link.redirect.RedirectTicketManager.RedirectGrant;
import link.srrrg.link.redirect.RedirectTicketManager.RejectReason;
import link.srrrg.link.redirect.RedirectTicketManager.Validation;
import link.srrrg.link.redirect.dto.RedirectCheckResponse;
import link.srrrg.link.redirect.dto.RedirectLink;
import link.srrrg.link.risk.UrlRiskCheckResult;
import link.srrrg.link.risk.UrlRiskChecker;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RedirectService {

	private final LinkRepository linkRepository;
	private final UrlValidator urlValidator;
	private final UrlRiskChecker urlRiskChecker;
	private final RedirectTicketManager redirectTicketManager;
	private final LinkClickEventRecorder clickEventRecorder;
	private final LinkRedirectEventRecorder redirectEventRecorder;
	private final TransactionOperations transactions;
	private final Duration verificationTtl;

	public RedirectService(LinkRepository linkRepository, UrlValidator urlValidator,
			UrlRiskChecker urlRiskChecker, RedirectTicketManager redirectTicketManager,
			LinkClickEventRecorder clickEventRecorder,
			LinkRedirectEventRecorder redirectEventRecorder, PlatformTransactionManager transactionManager,
			@Value("${srrrg.redirect.verification-ttl:1h}") Duration verificationTtl) {
		this(linkRepository, urlValidator, urlRiskChecker, redirectTicketManager,
				clickEventRecorder, redirectEventRecorder, new TransactionTemplate(transactionManager),
				verificationTtl);
	}

	public RedirectLink resolveRedirectPage(String code, ClientRequestInfo requestInfo) {
		log.debug("Secure Redirect page requested: code={}", code);
		Link link = findAvailableLink(code);
		urlValidator.validate(link.getOriginalUrl());
		CachedRedirect cachedRedirect = resolveCachedRedirect(link);
		log.debug("Secure Redirect page resolved: code={}, cachedVerification={}",
				code, cachedRedirect.status() != null);
		return new RedirectLink(link.getCode(), link.getOriginalUrl(),
				cachedRedirect.status(), cachedRedirect.redirectUrl());
	}

	public RedirectCheckResponse checkRedirect(String code, ClientRequestInfo requestInfo) {
		long startedAt = System.nanoTime();
		log.info("Redirect risk check started: code={}", code);
		Link initialLink = findAvailableLink(code);
		String checkedUrl = initialLink.getOriginalUrl();
		urlValidator.validate(checkedUrl);
		UrlRiskCheckResult result = urlRiskChecker.check(checkedUrl);

		RedirectCheckOutcome outcome = recordRedirectCheckResult(code, checkedUrl, result);
		if (!outcome.recorded()) {
			log.warn("Redirect risk check invalidated while recording: reason=URL_CHANGED, code={}, elapsedMs={}",
					code, elapsedMillis(startedAt));
			return new RedirectCheckResponse(UrlRiskCheckResult.CHECK_FAILED, null);
		}

		if (result == UrlRiskCheckResult.NO_THREAT_FOUND) {
			log.info("Redirect risk check completed: code={}, result={}, redirectReady=true, elapsedMs={}",
					code, result, elapsedMillis(startedAt));
			return new RedirectCheckResponse(result, outcome.redirectUrl());
		}
		if (result == UrlRiskCheckResult.CHECK_FAILED) {
			// 검사 실패 시에도 외부 URL을 직접 주지 않고 srrrg의 302 endpoint만 전달함.
			log.warn("Redirect risk check completed: code={}, result={}, manualRedirectAvailable=true, elapsedMs={}",
					code, result, elapsedMillis(startedAt));
			return new RedirectCheckResponse(result, outcome.redirectUrl());
		}
		// 위협 탐지 결과에는 목적지 URL을 포함하지 않음.
		log.warn("Redirect risk check completed: code={}, result={}, redirectBlocked=true, elapsedMs={}",
				code, result, elapsedMillis(startedAt));
		return new RedirectCheckResponse(result, null);
	}

	public String redirectToOriginal(String code, String ticket, ClientRequestInfo requestInfo) {
		return transactions.execute(status -> {
			Link link = findAvailableLink(code);
			urlValidator.validate(link.getOriginalUrl());
			Validation validation = redirectTicketManager.validate(
					link.getCode(), link.getOriginalUrl(), link.getSecretKeyHash(), ticket);
			validateRedirectTicket(link, validation);
			recordSuccessfulRedirect(link, requestInfo);
			log.info("Redirect issued: code={}, grant={}", code, validation.grant());
			return link.getOriginalUrl();
		});
	}

	private CachedRedirect resolveCachedRedirect(Link pageLink) {
		if (!hasFreshVerification(pageLink)) {
			return CachedRedirect.none();
		}

		return transactions.execute(status -> {
			// 페이지 렌더링 직후 링크가 수정될 수 있으므로 캐시 재사용 직전에 한 번 더 확인함.
			Link currentLink = linkRepository.findByCode(pageLink.getCode()).orElse(null);
			if (!matchesSameVerification(currentLink, pageLink)) {
				log.debug("Cached redirect verification not reused: reason=STALE_DATA, code={}",
						pageLink.getCode());
				return CachedRedirect.none();
			}

			log.info("Cached redirect verification reused: code={}, status={}, verifiedAt={}",
					currentLink.getCode(), currentLink.getStatus(), currentLink.getVerifiedAt());
			String redirectUrl = currentLink.getStatus() == LinkStatus.NO_THREAT_FOUND
					? redirectUrlFor(currentLink, RedirectGrant.SAFE)
					: null;
			return new CachedRedirect(currentLink.getStatus(), redirectUrl);
		});
	}

	private RedirectCheckOutcome recordRedirectCheckResult(String code, String checkedUrl, UrlRiskCheckResult result) {
		RedirectCheckOutcome outcome = transactions.execute(status -> {
			// Safe Browsing 호출 중 관리자가 URL을 바꾸면 예전 URL의 결과를 저장하거나 반환하지 않음.
			Link currentLink = findAvailableLink(code);
			if (!checkedUrl.equals(currentLink.getOriginalUrl())) {
				return RedirectCheckOutcome.stale();
			}
			urlValidator.validate(currentLink.getOriginalUrl());

			// 검사 실패는 일시 장애일 수 있으므로 캐시하지 않음. 성공/위협 탐지만 TTL 캐시 대상임.
			LinkStatus cacheableStatus = cacheableStatus(result);
			if (cacheableStatus != null && !updateVerification(code, checkedUrl, cacheableStatus)) {
				return RedirectCheckOutcome.stale();
			}

			if (result == UrlRiskCheckResult.NO_THREAT_FOUND) {
				return RedirectCheckOutcome.ready(redirectUrlFor(currentLink, RedirectGrant.SAFE));
			}
			if (result == UrlRiskCheckResult.CHECK_FAILED) {
				return RedirectCheckOutcome.ready(redirectUrlFor(currentLink, RedirectGrant.MANUAL));
			}
			return RedirectCheckOutcome.recordedWithoutRedirect();
		});
		return outcome == null ? RedirectCheckOutcome.stale() : outcome;
	}

	private boolean updateVerification(String code, String checkedUrl, LinkStatus status) {
		int updatedRows = linkRepository.updateVerificationByCodeAndOriginalUrl(
				code, checkedUrl, status, Instant.now());
		if (updatedRows != 1) {
			log.warn("Redirect verification result not recorded: reason=URL_CHANGED, code={}", code);
			return false;
		}
		return true;
	}

	private LinkStatus cacheableStatus(UrlRiskCheckResult result) {
		if (result == UrlRiskCheckResult.NO_THREAT_FOUND) {
			return LinkStatus.NO_THREAT_FOUND;
		}
		if (result == UrlRiskCheckResult.THREAT_DETECTED) {
			return LinkStatus.THREAT_DETECTED;
		}
		return null;
	}

	private boolean matchesSameVerification(Link currentLink, Link pageLink) {
		return currentLink != null
				&& !currentLink.isDeleted()
				&& !currentLink.isExpiredAt(Instant.now())
				&& currentLink.getOriginalUrl().equals(pageLink.getOriginalUrl())
				&& currentLink.getStatus() == pageLink.getStatus()
				&& pageLink.getVerifiedAt().equals(currentLink.getVerifiedAt());
	}

	private String redirectUrlFor(Link link, RedirectGrant grant) {
		return "/api/redirect/" + link.getCode()
				+ "?ticket=" + redirectTicketManager.issue(
						link.getCode(), link.getOriginalUrl(), link.getSecretKeyHash(), grant);
	}

	private void validateRedirectTicket(Link link, Validation validation) {
		if (!validation.accepted()) {
			log.warn("Redirect ticket rejected: reason={}, code={}", validation.rejectReason(), link.getCode());
			if (validation.rejectReason() == RejectReason.EXPIRED
					|| validation.rejectReason() == RejectReason.URL_CHANGED) {
				throw new LinkGoneException();
			}
			throw new LinkNotFoundException();
		}
		if (validation.grant() == RedirectGrant.SAFE && !hasFreshNoThreatVerification(link)) {
			log.warn("Redirect ticket rejected: reason=SAFE_CACHE_MISSING, code={}", link.getCode());
			throw new LinkGoneException();
		}
		if (validation.grant() == RedirectGrant.MANUAL && hasFreshThreatVerification(link)) {
			log.warn("Redirect ticket rejected: reason=FRESH_THREAT_CACHE, code={}", link.getCode());
			throw new LinkGoneException();
		}
	}

	private void recordSuccessfulRedirect(Link link, ClientRequestInfo requestInfo) {
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

	private Link findAvailableLink(String code) {
		Link link = linkRepository.findByCode(code).orElseThrow(() -> {
			log.info("Link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		boolean deleted = link.isDeleted();
		boolean expired = link.isExpiredAt(Instant.now());
		if (deleted || expired) {
			log.info("Link unavailable: code={}, deleted={}, expired={}", code, deleted, expired);
			throw new LinkGoneException();
		}
		return link;
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	private boolean hasFreshVerification(Link link) {
		// 검사 실패는 캐시하지 않는다. 일시 장애를 오래 재사용하면 검사 우회처럼 동작할 수 있음.
		return (hasFreshNoThreatVerification(link) || hasFreshThreatVerification(link));
	}

	private boolean hasFreshNoThreatVerification(Link link) {
		return link.getStatus() == LinkStatus.NO_THREAT_FOUND
				&& hasFreshVerifiedAt(link);
	}

	private boolean hasFreshThreatVerification(Link link) {
		return link.getStatus() == LinkStatus.THREAT_DETECTED
				&& hasFreshVerifiedAt(link);
	}

	private boolean hasFreshVerifiedAt(Link link) {
		return link.getVerifiedAt() != null
				&& link.getVerifiedAt().isAfter(Instant.now().minus(verificationTtl));
	}

	private record CachedRedirect(LinkStatus status, String redirectUrl) {
		private static CachedRedirect none() {
			return new CachedRedirect(null, null);
		}
	}

	private record RedirectCheckOutcome(boolean recorded, String redirectUrl) {
		private static RedirectCheckOutcome stale() {
			return new RedirectCheckOutcome(false, null);
		}

		private static RedirectCheckOutcome ready(String redirectUrl) {
			return new RedirectCheckOutcome(true, redirectUrl);
		}

		private static RedirectCheckOutcome recordedWithoutRedirect() {
			return new RedirectCheckOutcome(true, null);
		}
	}
}

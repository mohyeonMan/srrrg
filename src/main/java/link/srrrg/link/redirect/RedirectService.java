package link.srrrg.link.redirect;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import link.srrrg.link.Link;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.UrlValidator;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.LinkClickEventRecorder;
import link.srrrg.link.access.LinkRedirectEventRecorder;
import link.srrrg.link.risk.RiskVerdict;
import link.srrrg.link.risk.UrlRiskVerificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RedirectService {

	private final LinkRepository linkRepository;
	private final UrlValidator urlValidator;
	private final UrlRiskVerificationService riskVerificationService;
	private final LinkClickEventRecorder clickEventRecorder;
	private final LinkRedirectEventRecorder redirectEventRecorder;
	private final TransactionOperations transactions;

	public RedirectService(LinkRepository linkRepository, UrlValidator urlValidator,
			UrlRiskVerificationService riskVerificationService,
			LinkClickEventRecorder clickEventRecorder,
			LinkRedirectEventRecorder redirectEventRecorder,
			PlatformTransactionManager transactionManager) {
		this(linkRepository, urlValidator, riskVerificationService, clickEventRecorder,
				redirectEventRecorder, new TransactionTemplate(transactionManager));
	}

	public String redirect(String code, ClientRequestInfo requestInfo) {
		long startedAt = System.nanoTime();
		Link initialLink = findAvailableLink(code);
		String checkedUrl = initialLink.getOriginalUrl();
		urlValidator.validate(checkedUrl);

		RiskVerdict verdict = riskVerificationService.verify(checkedUrl).verdict();
		if (verdict == RiskVerdict.THREAT) {
			log.warn("Redirect blocked by URL risk verification: code={}, elapsedMs={}",
					code, elapsedMillis(startedAt));
			throw new UnsafeUrlException();
		}
		if (verdict == RiskVerdict.UNKNOWN) {
			log.warn("Redirect unavailable after URL risk verification: code={}, elapsedMs={}",
					code, elapsedMillis(startedAt));
			throw new UrlRiskCheckFailedException();
		}

		String redirectUrl = completeRedirect(code, checkedUrl, requestInfo);
		log.info("Redirect issued: code={}, elapsedMs={}", code, elapsedMillis(startedAt));
		return redirectUrl;
	}

	private String completeRedirect(String code, String checkedUrl, ClientRequestInfo requestInfo) {
		return transactions.execute(status -> {
			Link currentLink = findAvailableLink(code);
			if (!checkedUrl.equals(currentLink.getOriginalUrl())) {
				log.warn("Redirect verification invalidated: reason=URL_CHANGED, code={}", code);
				throw new UrlRiskCheckFailedException();
			}
			urlValidator.validate(currentLink.getOriginalUrl());
			recordSuccessfulRedirect(currentLink, requestInfo);
			return currentLink.getOriginalUrl();
		});
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
}

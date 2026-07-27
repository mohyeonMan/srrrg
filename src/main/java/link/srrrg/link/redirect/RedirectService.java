package link.srrrg.link.redirect;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Timer;
import link.srrrg.common.metrics.SrrrgMetrics;
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
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RedirectService {

	private final LinkRepository linkRepository;
	private final UrlValidator urlValidator;
	private final UrlRiskVerificationService riskVerificationService;
	private final LinkClickEventRecorder clickEventRecorder;
	private final LinkRedirectEventRecorder redirectEventRecorder;
	private final TransactionOperations transactions;
	private final SrrrgMetrics metrics;

	@Autowired
	public RedirectService(LinkRepository linkRepository, UrlValidator urlValidator,
			UrlRiskVerificationService riskVerificationService,
			LinkClickEventRecorder clickEventRecorder,
			LinkRedirectEventRecorder redirectEventRecorder,
			PlatformTransactionManager transactionManager,
			SrrrgMetrics metrics) {
		this(linkRepository, urlValidator, riskVerificationService, clickEventRecorder,
				redirectEventRecorder, new TransactionTemplate(transactionManager), metrics);
	}

	RedirectService(LinkRepository linkRepository, UrlValidator urlValidator,
			UrlRiskVerificationService riskVerificationService,
			LinkClickEventRecorder clickEventRecorder,
			LinkRedirectEventRecorder redirectEventRecorder,
			TransactionOperations transactions,
			SrrrgMetrics metrics) {
		this.linkRepository = linkRepository;
		this.urlValidator = urlValidator;
		this.riskVerificationService = riskVerificationService;
		this.clickEventRecorder = clickEventRecorder;
		this.redirectEventRecorder = redirectEventRecorder;
		this.transactions = transactions;
		this.metrics = metrics;
	}

	public String redirect(String code, ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		long startedAt = System.nanoTime();
		try {
			Link initialLink = findAvailableLink(code);
			String checkedUrl = initialLink.getOriginalUrl();
			urlValidator.validate(checkedUrl);
			recordClick(initialLink, requestInfo);

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
			outcome = "redirected";
			return redirectUrl;
		} catch (UnsafeUrlException exception) {
			outcome = "blocked";
			throw exception;
		} catch (LinkNotFoundException exception) {
			outcome = "not_found";
			throw exception;
		} catch (LinkGoneException exception) {
			outcome = "gone";
			throw exception;
		} catch (UrlRiskCheckFailedException exception) {
			outcome = "check_failed";
			throw exception;
		} finally {
			metrics.recordRedirect(sample, outcome);
		}
	}

	private String completeRedirect(String code, String checkedUrl, ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			String redirectUrl = transactions.execute(status -> {
				Link currentLink = findAvailableLink(code);
				if (!checkedUrl.equals(currentLink.getOriginalUrl())) {
					log.warn("Redirect verification invalidated: reason=URL_CHANGED, code={}", code);
					throw new UrlRiskCheckFailedException();
				}
				urlValidator.validate(currentLink.getOriginalUrl());
				recordSuccessfulRedirect(currentLink, requestInfo);
				return currentLink.getOriginalUrl();
			});
			outcome = "success";
			return redirectUrl;
		} finally {
			metrics.recordRedirectWrite(sample, "redirect", outcome);
		}
	}

	private void recordSuccessfulRedirect(Link link, ClientRequestInfo requestInfo) {
		redirectEventRecorder.record(link, requestInfo);

		int redirectUpdates = linkRepository.incrementRedirectCountByCode(link.getCode());
		if (redirectUpdates != 1) {
			log.warn("Redirect statistics update failed: code={}, redirectUpdates={}",
					link.getCode(), redirectUpdates);
			throw new LinkNotFoundException();
		}
	}

	private void recordClick(Link link, ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			transactions.executeWithoutResult(status -> {
				clickEventRecorder.record(link, requestInfo);
				int clickUpdates = linkRepository.incrementClickCountByCode(link.getCode());
				if (clickUpdates != 1) {
					log.warn("Click statistics update failed: code={}, clickUpdates={}", link.getCode(), clickUpdates);
					throw new LinkNotFoundException();
				}
			});
			outcome = "success";
		} finally {
			metrics.recordRedirectWrite(sample, "click", outcome);
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
			throw new LinkGoneException(deleted ? LinkGoneException.Reason.DELETED : LinkGoneException.Reason.EXPIRED);
		}
		return link;
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}

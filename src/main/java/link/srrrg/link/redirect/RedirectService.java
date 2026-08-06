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
import link.srrrg.link.access.LinkAccessEvent.Outcome;
import link.srrrg.link.access.LinkAccessEventRecorder;
import link.srrrg.link.risk.RiskVerdict;
import link.srrrg.link.risk.UrlRiskVerificationService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RedirectService {

	private final LinkRepository linkRepository;
	private final UrlValidator urlValidator;
	private final UrlRiskVerificationService riskVerificationService;
	private final LinkAccessEventRecorder accessEventRecorder;
	private final TransactionOperations transactions;
	private final SrrrgMetrics metrics;

	@Autowired
	public RedirectService(LinkRepository linkRepository, UrlValidator urlValidator,
			UrlRiskVerificationService riskVerificationService,
			LinkAccessEventRecorder accessEventRecorder,
			PlatformTransactionManager transactionManager,
			SrrrgMetrics metrics) {
		this(linkRepository, urlValidator, riskVerificationService, accessEventRecorder,
				new TransactionTemplate(transactionManager), metrics);
	}

	RedirectService(LinkRepository linkRepository, UrlValidator urlValidator,
			UrlRiskVerificationService riskVerificationService,
			LinkAccessEventRecorder accessEventRecorder,
			TransactionOperations transactions,
			SrrrgMetrics metrics) {
		this.linkRepository = linkRepository;
		this.urlValidator = urlValidator;
		this.riskVerificationService = riskVerificationService;
		this.accessEventRecorder = accessEventRecorder;
		this.transactions = transactions;
		this.metrics = metrics;
	}

	public String redirect(String code, ClientRequestInfo requestInfo) {
		Instant accessedAt = Instant.now();
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		long startedAt = System.nanoTime();
		try {
			Link initialLink = findAvailableLink(code);
			String checkedUrl = initialLink.getOriginalUrl();
			urlValidator.validate(checkedUrl);

			RiskVerdict verdict = riskVerificationService.verify(checkedUrl).verdict();
			if (verdict == RiskVerdict.THREAT) {
				recordAccess(initialLink, accessedAt, Outcome.BLOCKED, requestInfo);
				log.warn("Redirect blocked by URL risk verification: code={}, elapsedMs={}",
						code, elapsedMillis(startedAt));
				throw new UnsafeUrlException();
			}
			if (verdict == RiskVerdict.UNKNOWN) {
				recordAccess(initialLink, accessedAt, Outcome.CHECK_FAILED, requestInfo);
				log.warn("Redirect unavailable after URL risk verification: code={}, elapsedMs={}",
						code, elapsedMillis(startedAt));
				throw new UrlRiskCheckFailedException();
			}

			String redirectUrl = completeRedirect(code, checkedUrl, accessedAt, requestInfo);
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

	private String completeRedirect(String code, String checkedUrl, Instant accessedAt,
			ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			String redirectUrl = transactions.execute(status -> {
				Link currentLink = findAvailableLink(code);
				if (!checkedUrl.equals(currentLink.getOriginalUrl())) {
					log.warn("Redirect verification invalidated: reason=URL_CHANGED, code={}", code);
					accessEventRecorder.record(currentLink, accessedAt, Outcome.URL_CHANGED, requestInfo);
					incrementAccessCount(code);
					return null;
				}
				accessEventRecorder.record(currentLink, accessedAt, Outcome.REDIRECTED, requestInfo);
				int updates = linkRepository.incrementAccessAndRedirectCountsByCode(code);
				if (updates != 1) {
					log.warn("Access statistics update failed: code={}, updates={}", code, updates);
					throw new LinkNotFoundException();
				}
				return currentLink.getOriginalUrl();
			});
			outcome = "success";
			if (redirectUrl == null) {
				throw new UrlRiskCheckFailedException();
			}
			return redirectUrl;
		} finally {
			metrics.recordRedirectWrite(sample, "access", outcome);
		}
	}

	private void recordAccess(Link link, Instant accessedAt, Outcome accessOutcome,
			ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			transactions.executeWithoutResult(status -> {
				accessEventRecorder.record(link, accessedAt, accessOutcome, requestInfo);
				incrementAccessCount(link.getCode());
			});
			outcome = "success";
		} finally {
			metrics.recordRedirectWrite(sample, "access", outcome);
		}
	}

	private void incrementAccessCount(String code) {
		int updates = linkRepository.incrementAccessCountByCode(code);
		if (updates != 1) {
			log.warn("Access statistics update failed: code={}, updates={}", code, updates);
			throw new LinkNotFoundException();
		}
	}

	private Link findAvailableLink(String code) {
		Link link = linkRepository.findByCode(code).orElseThrow(() -> {
			log.info("Link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		boolean deleted = link.isDeleted() || link.getProject() != null && link.getProject().getArchivedAt() != null;
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

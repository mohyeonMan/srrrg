package link.srrrg.link.redirect;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Timer;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.domain.ProjectDomainService;
import link.srrrg.domain.ProjectDomainService.HostRoute;
import link.srrrg.link.DestinationUrlMerger;
import link.srrrg.link.Link;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkUtmValueRepository;
import link.srrrg.link.LinkUtmValueRepository.EffectiveUtmValue;
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
	private final LinkUtmValueRepository linkUtmValueRepository;
	private final UrlValidator urlValidator;
	private final UrlRiskVerificationService riskVerificationService;
	private final LinkAccessEventRecorder accessEventRecorder;
	private final TransactionOperations transactions;
	private final SrrrgMetrics metrics;
	private final ProjectDomainService domains;

	@Autowired
	public RedirectService(LinkRepository linkRepository, LinkUtmValueRepository linkUtmValueRepository,
			UrlValidator urlValidator, UrlRiskVerificationService riskVerificationService,
			LinkAccessEventRecorder accessEventRecorder,
			PlatformTransactionManager transactionManager,
			SrrrgMetrics metrics, ProjectDomainService domains) {
		this(linkRepository, linkUtmValueRepository, urlValidator, riskVerificationService, accessEventRecorder,
				new TransactionTemplate(transactionManager), metrics, domains);
	}

	RedirectService(LinkRepository linkRepository, LinkUtmValueRepository linkUtmValueRepository,
			UrlValidator urlValidator, UrlRiskVerificationService riskVerificationService,
			LinkAccessEventRecorder accessEventRecorder,
			TransactionOperations transactions,
			SrrrgMetrics metrics, ProjectDomainService domains) {
		this.linkRepository = linkRepository;
		this.linkUtmValueRepository = linkUtmValueRepository;
		this.urlValidator = urlValidator;
		this.riskVerificationService = riskVerificationService;
		this.accessEventRecorder = accessEventRecorder;
		this.transactions = transactions;
		this.metrics = metrics;
		this.domains = domains;
	}

	public String redirect(String host, String code, ClientRequestInfo requestInfo) {
		Instant accessedAt = Instant.now();
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		long startedAt = System.nanoTime();
		try {
			HostRoute route = domains.resolve(host).orElseThrow(LinkNotFoundException::new);
			Link initialLink = findLink(route, code);
			if (isDeleted(initialLink)) throw unavailable(code, initialLink, false);
			if (initialLink.isExpiredAt(accessedAt)) {
				recordAccess(initialLink, accessedAt, Outcome.EXPIRED, requestInfo);
				throw unavailable(code, initialLink, true);
			}
			String rawUrl = effectiveOriginalUrl(initialLink);
			String checkedUrl = initialLink.getProject() == null
					? DestinationUrlMerger.merge(rawUrl, utmValuesFor(initialLink))
					: rawUrl;
			urlValidator.validate(checkedUrl);

			if (initialLink.getProject() == null) {
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
			}
			// 프로젝트 링크는 인증된 멤버 또는 API key 생성자를 신뢰해 위험 검사를 생략한다.
			// 신뢰 정책이 바뀌면 위 검사의 조건을 제거해 모든 링크에 다시 적용한다.

			String redirectUrl = completeRedirect(route, code, rawUrl, checkedUrl, accessedAt, requestInfo);
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

	private String completeRedirect(HostRoute route, String code, String checkedRawUrl, String checkedMergedUrl,
			Instant accessedAt, ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			String redirectUrl = transactions.execute(status -> {
				Link currentLink = findAvailableLink(route, code, accessedAt);
				if (!checkedRawUrl.equals(effectiveOriginalUrl(currentLink))) {
					log.warn("Redirect verification invalidated: reason=URL_CHANGED, code={}", code);
					accessEventRecorder.record(currentLink, accessedAt, Outcome.URL_CHANGED, requestInfo);
					incrementAccessCount(currentLink.getId());
					return null;
				}
				Map<String, String> effectiveUtm = currentLink.getCampaign() == null ? Map.of() : utmValuesFor(currentLink);
				accessEventRecorder.record(currentLink, accessedAt, Outcome.REDIRECTED, requestInfo, effectiveUtm);
				int updates = linkRepository.incrementAccessAndRedirectCountsById(currentLink.getId());
				if (updates != 1) {
					log.warn("Access statistics update failed: code={}, updates={}", code, updates);
					throw new LinkNotFoundException();
				}
				if (currentLink.getProject() == null) return checkedMergedUrl;
				String currentUrl = effectiveOriginalUrl(currentLink);
				return currentLink.getCampaign() == null
						? currentUrl
						: DestinationUrlMerger.merge(currentUrl, effectiveUtm);
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

	private String effectiveOriginalUrl(Link link) {
		if (link.getOriginalUrl() != null) return link.getOriginalUrl();
		if (link.getCampaign() != null && link.getCampaign().getDefaultOriginalUrl() != null) {
			return link.getCampaign().getDefaultOriginalUrl();
		}
		throw new LinkGoneException(LinkGoneException.Reason.NO_DESTINATION);
	}

	private Map<String, String> utmValuesFor(Link link) {
		Map<String, String> values = new LinkedHashMap<>();
		for (EffectiveUtmValue effective : linkUtmValueRepository.findEffectiveByLinkId(link.getId())) {
			values.put(effective.getFieldName(), effective.getValue());
		}
		return values;
	}

	private void recordAccess(Link link, Instant accessedAt, Outcome accessOutcome,
			ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			transactions.executeWithoutResult(status -> {
				accessEventRecorder.record(link, accessedAt, accessOutcome, requestInfo);
				incrementAccessCount(link.getId());
			});
			outcome = "success";
		} finally {
			metrics.recordRedirectWrite(sample, "access", outcome);
		}
	}

	private void incrementAccessCount(Long linkId) {
		int updates = linkRepository.incrementAccessCountById(linkId);
		if (updates != 1) {
			log.warn("Access statistics update failed: linkId={}, updates={}", linkId, updates);
			throw new LinkNotFoundException();
		}
	}

	private Link findLink(HostRoute route, String code) {
		Link link = (route.isBaseDomain()
				? linkRepository.findByCodeAndProjectIsNull(code)
				: linkRepository.findByHostnameAndCode(route.hostname(), code)).orElseThrow(() -> {
			log.info("Link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		return link;
	}

	private Link findAvailableLink(HostRoute route, String code, Instant accessedAt) {
		Link link = findLink(route, code);
		if (isDeleted(link)) throw unavailable(code, link, false);
		if (link.isExpiredAt(accessedAt)) throw unavailable(code, link, true);
		return link;
	}

	private boolean isDeleted(Link link) {
		return link.isDeleted() || link.getProject() != null && link.getProject().getArchivedAt() != null;
	}

	private LinkGoneException unavailable(String code, Link link, boolean expired) {
		log.info("Link unavailable: code={}, deleted={}, expired={}", code, isDeleted(link), expired);
		return new LinkGoneException(expired ? LinkGoneException.Reason.EXPIRED : LinkGoneException.Reason.DELETED);
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}

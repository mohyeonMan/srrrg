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
			if (belongsToDeletedProject(initialLink)) throw projectDeleted(code);
			if (initialLink.isExpiredAt(accessedAt)) {
				recordAccess(initialLink, accessedAt, Outcome.EXPIRED, requestInfo);
				throw expired(code);
			}
			String rawUrl = effectiveOriginalUrl(initialLink);
			String checkedUrl = initialLink.isAnonymous()
					? DestinationUrlMerger.merge(rawUrl, utmValuesFor(initialLink))
					: rawUrl;
			urlValidator.validate(checkedUrl);

			if (initialLink.isAnonymous()) {
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
					return null;
				}
				Map<String, String> effectiveUtm = currentLink.getCampaign() == null ? Map.of() : utmValuesFor(currentLink);
				accessEventRecorder.record(currentLink, accessedAt, Outcome.REDIRECTED, requestInfo, effectiveUtm);
				if (currentLink.isAnonymous()) return checkedMergedUrl;
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
			transactions.executeWithoutResult(status -> accessEventRecorder.record(link, accessedAt, accessOutcome, requestInfo));
			outcome = "success";
		} finally {
			metrics.recordRedirectWrite(sample, "access", outcome);
		}
	}

	private Link findLink(HostRoute route, String code) {
		Link link = (route.isBaseDomain()
				? linkRepository.findBySubdomainIsNullAndCode(code)
				: linkRepository.findBySubdomainAndCode(route.subdomain(), code)).orElseThrow(() -> {
			log.info("Link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		return link;
	}

	private Link findAvailableLink(HostRoute route, String code, Instant accessedAt) {
		Link link = findLink(route, code);
		if (belongsToDeletedProject(link)) throw projectDeleted(code);
		if (link.isExpiredAt(accessedAt)) throw expired(code);
		return link;
	}

	/**
	 * 삭제된 링크 자체는 {@code @SoftDelete}가 조회에서 걸러내므로 여기서는 프로젝트만 본다.
	 * 프로젝트가 삭제되면 연관이 비어 오는데, 익명 링크는 원래 project가 없으므로
	 * secret key 보유 여부({@link Link#isAnonymous()})로 구분해야 한다.
	 */
	private boolean belongsToDeletedProject(Link link) {
		return !link.isAnonymous() && link.getProject() == null;
	}

	private LinkNotFoundException projectDeleted(String code) {
		log.info("Link unavailable: reason=PROJECT_DELETED, code={}", code);
		return new LinkNotFoundException();
	}

	private LinkGoneException expired(String code) {
		log.info("Link unavailable: reason=EXPIRED, code={}", code);
		return new LinkGoneException(LinkGoneException.Reason.EXPIRED);
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}

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

/**
 * 공개 단축 URL 요청 한 건의 조회, 목적지 결정, 보안 검사, 접근 기록을 조율한다.
 * 이 진입점에는 인증이 없으므로 요청의 {@code Host}와 단축 코드가 어떤 링크를 가리키는지 먼저 확정하고,
 * 저장할 때 검증했던 값만 믿지 않고 실제 리다이렉트 시점의 URL도 다시 검증한다.
 *
 * <p>Safe Browsing 같은 외부 호출은 DB 트랜잭션 밖에서 수행한다. 느린 외부 응답을 기다리는 동안
 * 커넥션을 점유하지 않기 위해서다. 그 대신 응답 직전의 짧은 트랜잭션에서 링크를 다시 조회하여
 * 검사 중 목적지·만료·삭제 상태가 바뀌는 경쟁 조건을 차단한다.</p>
 *
 * <p>익명 링크는 신뢰할 수 없는 입력으로 보고 URL 위험 검사를 수행한다. 프로젝트 링크는 인증된 멤버나
 * API 키를 통해 생성된다는 현재 정책에 따라 위험 검사를 생략하지만, URL 형식과 SSRF 방어 검증은
 * 링크 종류와 관계없이 항상 수행한다.</p>
 */
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

	/**
	 * 호스트별 코드 공간에서 링크를 찾아 현재 요청에 사용할 최종 목적지 URL을 반환한다.
	 * 만료·위험·검사 실패 같은 종료 경로도 접근 이벤트와 메트릭에 각기 다른 결과로 남긴다.
	 *
	 * @param host 요청의 Host 헤더에서 얻은 호스트명
	 * @param code URL 경로에서 추출한 단축 코드
	 * @param requestInfo 통계 기록에 사용할 IP, Referer, User-Agent 정보
	 * @return 검증과 접근 기록을 모두 마친 최종 리다이렉트 URL
	 */
	public String redirect(String host, String code, ClientRequestInfo requestInfo) {
		// 한 요청의 만료 판정과 접근 이벤트가 서로 다른 시각을 기준으로 기록되지 않게 한 번만 읽는다.
		Instant accessedAt = Instant.now();
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		long startedAt = System.nanoTime();
		try {
			// 같은 코드라도 베이스 도메인과 프로젝트 서브도메인에서는 서로 다른 링크일 수 있다.
			HostRoute route = domains.resolve(host).orElseThrow(LinkNotFoundException::new);
			Link initialLink = findLink(route, code);
			// 삭제된 링크·프로젝트는 존재 여부를 숨기는 404로 끝내며 통계 이벤트도 만들지 않는다.
			if (belongsToDeletedProject(initialLink)) throw projectDeleted(code);
			if (initialLink.isExpiredAt(accessedAt)) {
				// 만료 링크는 410이지만, 아직 유입이 있는지 알 수 있도록 접근 자체는 기록한다.
				recordAccess(initialLink, accessedAt, Outcome.EXPIRED, requestInfo);
				throw expired(code);
			}
			// 링크 자체 목적지가 없으면 캠페인의 현재 기본 목적지를 상속한다.
			String rawUrl = effectiveOriginalUrl(initialLink);
			// 익명 링크는 실제로 이동할 UTM 병합 결과 전체가 위험 검사의 대상이어야 한다.
			String checkedUrl = initialLink.isAnonymous()
					? DestinationUrlMerger.merge(rawUrl, utmValuesFor(initialLink))
					: rawUrl;
			// 저장 후 데이터가 바뀌었을 가능성까지 고려해 프로토콜·호스트·사설망 차단을 다시 확인한다.
			urlValidator.validate(checkedUrl);

			if (initialLink.isAnonymous()) {
				// UNKNOWN은 안전 판정이 아니다. 검사 서비스가 복구될 때까지 fail-closed로 리다이렉트를 막는다.
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

			// Safe Browsing 같은 외부 호출 동안 DB 연결을 잡지 않고, 쓰기가 필요한 마지막 구간만 묶는다.
			String redirectUrl = completeRedirect(route, code, rawUrl, checkedUrl, accessedAt, requestInfo);
			log.info("Redirect issued: code={}, elapsedMs={}", code, elapsedMillis(startedAt));
			outcome = "redirected";
			return redirectUrl;
		// 예외 종류를 메트릭 태그로 번역하고 원래 예외는 전용 예외 처리기로 그대로 전달한다.
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

	/**
	 * 보안 검사를 통과한 링크를 트랜잭션 안에서 다시 확인하고 접근 이벤트와 최종 URL을 확정한다.
	 * 외부 검사 이후 상태가 달라졌다면 검사 결과를 재사용하지 않는다.
	 */
	private String completeRedirect(HostRoute route, String code, String checkedRawUrl, String checkedMergedUrl,
			Instant accessedAt, ClientRequestInfo requestInfo) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			String redirectUrl = transactions.execute(status -> {
				// 위험 검사와 트랜잭션 시작 사이에 링크 상태가 바뀔 수 있으므로 DB를 다시 읽어 확정한다.
				Link currentLink = findAvailableLink(route, code, accessedAt);
				if (!checkedRawUrl.equals(effectiveOriginalUrl(currentLink))) {
					// 검사하지 않은 새 목적지로 보내는 TOCTOU(time-of-check to time-of-use) 경쟁을 차단한다.
					log.warn("Redirect verification invalidated: reason=URL_CHANGED, code={}", code);
					accessEventRecorder.record(currentLink, accessedAt, Outcome.URL_CHANGED, requestInfo);
					// 여기서 예외를 던지면 이벤트도 롤백되므로 null을 신호로 반환해 먼저 커밋한다.
					return null;
				}
				// 캠페인 기본 UTM은 바뀔 수 있으므로 리다이렉트가 확정되는 시점의 유효값을 기록한다.
				Map<String, String> effectiveUtm = currentLink.getCampaign() == null ? Map.of() : utmValuesFor(currentLink);
				accessEventRecorder.record(currentLink, accessedAt, Outcome.REDIRECTED, requestInfo, effectiveUtm);
				// 익명 링크는 검사한 문자열 그대로, 신뢰하는 프로젝트 링크만 현재 UTM을 합쳐 반환한다.
				if (currentLink.isAnonymous()) return checkedMergedUrl;
				String currentUrl = effectiveOriginalUrl(currentLink);
				return currentLink.getCampaign() == null
						? currentUrl
						: DestinationUrlMerger.merge(currentUrl, effectiveUtm);
			});
			outcome = "success";
			if (redirectUrl == null) {
				// URL_CHANGED 이벤트의 커밋이 끝난 뒤 사용자에게 재시도 가능한 검사 실패를 알린다.
				throw new UrlRiskCheckFailedException();
			}
			return redirectUrl;
		} finally {
			metrics.recordRedirectWrite(sample, "access", outcome);
		}
	}

	/**
	 * 링크가 직접 가진 목적지를 우선하고, 없으면 캠페인 기본 목적지를 사용한다.
	 * 두 값이 모두 없으면 링크는 존재하지만 이동할 곳이 없으므로 410 Gone으로 처리한다.
	 */
	private String effectiveOriginalUrl(Link link) {
		if (link.getOriginalUrl() != null) return link.getOriginalUrl();
		if (link.getCampaign() != null && link.getCampaign().getDefaultOriginalUrl() != null) {
			return link.getCampaign().getDefaultOriginalUrl();
		}
		throw new LinkGoneException(LinkGoneException.Reason.NO_DESTINATION);
	}

	/**
	 * 링크별 UTM 값과 캠페인 기본값이 합쳐진 현재 유효값을 순서가 보존되는 Map으로 만든다.
	 * 실제 우선순위 계산은 조회 쿼리가 담당하며 링크별 값이 캠페인 기본값을 덮어쓴다.
	 */
	private Map<String, String> utmValuesFor(Link link) {
		Map<String, String> values = new LinkedHashMap<>();
		for (EffectiveUtmValue effective : linkUtmValueRepository.findEffectiveByLinkId(link.getId())) {
			values.put(effective.getFieldName(), effective.getValue());
		}
		return values;
	}

	/**
	 * 최종 리다이렉트 전에 종료되는 접근을 독립된 짧은 트랜잭션으로 기록한다.
	 * 기록 후 호출자가 예외를 던져도 BLOCKED·CHECK_FAILED·EXPIRED 이벤트가 함께 롤백되지 않는다.
	 */
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

	/**
	 * 해석된 호스트 경로에 따라 베이스 도메인과 서브도메인의 코드 공간을 분리해 조회한다.
	 * 저장소의 {@code @SoftDelete} 필터가 삭제된 링크를 제외하므로 조회 실패는 모두 404로 통일한다.
	 */
	private Link findLink(HostRoute route, String code) {
		Link link = (route.isBaseDomain()
				? linkRepository.findBySubdomainIsNullAndCode(code)
				: linkRepository.findBySubdomainAndCode(route.subdomain(), code)).orElseThrow(() -> {
			log.info("Link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		return link;
	}

	/**
	 * 외부 검사가 끝난 뒤 링크를 다시 조회하여 삭제된 프로젝트와 만료 여부를 재확인한다.
	 * 여러 파드가 동시에 요청과 수정을 처리하므로 최초 조회 결과만으로는 최종 상태를 보장할 수 없다.
	 */
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

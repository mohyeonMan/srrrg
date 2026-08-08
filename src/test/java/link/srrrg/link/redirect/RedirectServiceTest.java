package link.srrrg.link.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.domain.ProjectDomainService;
import link.srrrg.domain.ProjectDomainService.HostRoute;
import link.srrrg.link.Link;
import link.srrrg.link.LinkGoneException;
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
import link.srrrg.link.risk.UrlRiskAssessment;
import link.srrrg.link.risk.UrlRiskVerificationService;
import link.srrrg.campaign.Campaign;
import link.srrrg.project.Project;

class RedirectServiceTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final LinkUtmValueRepository linkUtmValueRepository = mock(LinkUtmValueRepository.class);
	private final UrlValidator validator = mock(UrlValidator.class);
	private final UrlRiskVerificationService riskVerificationService = mock(UrlRiskVerificationService.class);
	private final LinkAccessEventRecorder accessRecorder = mock(LinkAccessEventRecorder.class);
	private final ProjectDomainService domains = mock(ProjectDomainService.class);
	private final TransactionOperations transactions = new TransactionOperations() {
		@Override
		public <T> T execute(TransactionCallback<T> action) {
			return action.doInTransaction(new SimpleTransactionStatus());
		}
	};
	private final ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "agent");
	private SimpleMeterRegistry registry;
	private SrrrgMetrics metrics;
	private RedirectService service;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		metrics = new SrrrgMetrics(registry);
		service = new RedirectService(repository, linkUtmValueRepository, validator, riskVerificationService,
				accessRecorder, transactions, metrics, domains);
		when(domains.resolve("srrrg.link")).thenReturn(Optional.of(new HostRoute(null)));
	}

	@Test
	void springSelectsThePlatformTransactionManagerConstructor() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.registerBean(LinkRepository.class, () -> repository);
			context.registerBean(LinkUtmValueRepository.class, () -> linkUtmValueRepository);
			context.registerBean(UrlValidator.class, () -> validator);
			context.registerBean(UrlRiskVerificationService.class, () -> riskVerificationService);
			context.registerBean(LinkAccessEventRecorder.class, () -> accessRecorder);
			context.registerBean(PlatformTransactionManager.class,
					() -> mock(PlatformTransactionManager.class));
			context.registerBean(SrrrgMetrics.class, () -> metrics);
			context.registerBean(ProjectDomainService.class, () -> domains);
			context.registerBean(RedirectService.class);

			context.refresh();

			assertThat(context.getBean(RedirectService.class)).isNotNull();
		}
	}

	@Test
	void safeUrlRecordsRedirectAndReturnsOriginalUrl() {
		Link link = availableLink("https://example.com/path?q=1");
		when(riskVerificationService.verify(link.getOriginalUrl())).thenReturn(assessment(RiskVerdict.SAFE));
		when(repository.incrementAccessAndRedirectCountsById(7L)).thenReturn(1);

		String redirectUrl = service.redirect("srrrg.link", "aB3x9Q", requestInfo);

		assertThat(redirectUrl).isEqualTo("https://example.com/path?q=1");
		verify(accessRecorder).record(eq(link), any(Instant.class), eq(Outcome.REDIRECTED), eq(requestInfo), eq(Map.of()));
		verify(repository).incrementAccessAndRedirectCountsById(7L);
		assertTimerCount("srrrg.redirect", "outcome", "redirected", 1);
		assertWriteTimerCount("success", 1);
	}

	@Test
	void projectDomainRoutesByHostnameAndCode() {
		Link link = link("https://project.example");
		when(link.getProject()).thenReturn(mock(Project.class));
		when(domains.resolve("acme.srrrg.link")).thenReturn(Optional.of(new HostRoute("acme.srrrg.link")));
		when(repository.findByHostnameAndCode("acme.srrrg.link", "aB3x9Q")).thenReturn(Optional.of(link));
		when(repository.incrementAccessAndRedirectCountsById(7L)).thenReturn(1);

		assertThat(service.redirect("acme.srrrg.link", "aB3x9Q", requestInfo))
				.isEqualTo("https://project.example");
		verify(repository, never()).findByCodeAndProjectIsNull(any());
		verify(riskVerificationService, never()).verify(any());
	}

	@Test
	void campaignLinkWithoutOwnUrlUsesCurrentCampaignDefault() {
		Link link = link(null);
		Campaign campaign = mock(Campaign.class);
		when(campaign.getDefaultOriginalUrl()).thenReturn("https://current.example/default");
		when(link.getCampaign()).thenReturn(campaign);
		when(link.getProject()).thenReturn(mock(Project.class));
		when(domains.resolve("acme.srrrg.link")).thenReturn(Optional.of(new HostRoute("acme.srrrg.link")));
		when(repository.findByHostnameAndCode("acme.srrrg.link", "aB3x9Q")).thenReturn(Optional.of(link));
		when(repository.incrementAccessAndRedirectCountsById(7L)).thenReturn(1);
		EffectiveUtmValue utm = mock(EffectiveUtmValue.class);
		when(utm.getFieldName()).thenReturn("utm_source");
		when(utm.getValue()).thenReturn("campaign-default");
		when(linkUtmValueRepository.findEffectiveByLinkId(7L)).thenReturn(List.of(utm));

		assertThat(service.redirect("acme.srrrg.link", "aB3x9Q", requestInfo))
				.isEqualTo("https://current.example/default?utm_source=campaign-default");
		verify(accessRecorder).record(eq(link), any(Instant.class), eq(Outcome.REDIRECTED), eq(requestInfo),
				eq(Map.of("utm_source", "campaign-default")));
		verify(riskVerificationService, never()).verify(any());
		verify(linkUtmValueRepository, times(1)).findEffectiveByLinkId(7L);
	}

	@Test
	void campaignLinkWithoutOwnOrDefaultUrlReturnsGone() {
		Link link = link(null);
		when(link.getCampaign()).thenReturn(mock(Campaign.class));
		when(link.getProject()).thenReturn(mock(Project.class));
		when(domains.resolve("acme.srrrg.link")).thenReturn(Optional.of(new HostRoute("acme.srrrg.link")));
		when(repository.findByHostnameAndCode("acme.srrrg.link", "aB3x9Q")).thenReturn(Optional.of(link));

		assertThatThrownBy(() -> service.redirect("acme.srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(LinkGoneException.class)
				.extracting(exception -> ((LinkGoneException) exception).getReason())
				.isEqualTo(LinkGoneException.Reason.NO_DESTINATION);
	}

	@Test
	void anotherProjectDomainDoesNotResolveTheLink() {
		when(domains.resolve("other.srrrg.link")).thenReturn(Optional.of(new HostRoute("other.srrrg.link")));
		when(repository.findByHostnameAndCode("other.srrrg.link", "aB3x9Q")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.redirect("other.srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(link.srrrg.link.LinkNotFoundException.class);
		verify(repository, never()).findByHostnameAndCode("acme.srrrg.link", "aB3x9Q");
	}

	@Test
	void unregisteredHostDoesNotQueryLinks() {
		when(domains.resolve("unknown.srrrg.link")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.redirect("unknown.srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(link.srrrg.link.LinkNotFoundException.class);
		verify(repository, never()).findByCodeAndProjectIsNull(any());
		verify(repository, never()).findByHostnameAndCode(any(), any());
	}

	@Test
	void threatUrlRecordsBlockedAccess() {
		Link link = availableLink("https://bad.example");
		when(riskVerificationService.verify("https://bad.example")).thenReturn(assessment(RiskVerdict.THREAT));
		when(repository.incrementAccessCountById(7L)).thenReturn(1);

		assertThatThrownBy(() -> service.redirect("srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(UnsafeUrlException.class);
		verify(accessRecorder).record(eq(link), any(Instant.class), eq(Outcome.BLOCKED), eq(requestInfo));
		verify(repository, never()).incrementAccessAndRedirectCountsById(any());
		assertTimerCount("srrrg.redirect", "outcome", "blocked", 1);
		assertWriteTimerCount("success", 1);
	}

	@Test
	void unknownUrlRecordsFailedAccess() {
		Link link = availableLink("https://example.com");
		when(riskVerificationService.verify("https://example.com"))
				.thenReturn(UrlRiskAssessment.unknown(Instant.now()));
		when(repository.incrementAccessCountById(7L)).thenReturn(1);

		assertThatThrownBy(() -> service.redirect("srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(accessRecorder).record(eq(link), any(Instant.class), eq(Outcome.CHECK_FAILED), eq(requestInfo));
		verify(repository, never()).incrementAccessAndRedirectCountsById(any());
		assertTimerCount("srrrg.redirect", "outcome", "check_failed", 1);
		assertWriteTimerCount("success", 1);
	}

	@Test
	void expiredLinkReportsExpiredReason() {
		Link link = link("https://example.com");
		when(link.isExpiredAt(any(Instant.class))).thenReturn(true);
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.of(link));
		when(repository.incrementAccessCountById(7L)).thenReturn(1);

		assertThatThrownBy(() -> service.redirect("srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(LinkGoneException.class)
				.extracting(exception -> ((LinkGoneException) exception).getReason())
				.isEqualTo(LinkGoneException.Reason.EXPIRED);
		verify(accessRecorder).record(eq(link), any(Instant.class), eq(Outcome.EXPIRED), eq(requestInfo));
		verify(repository).incrementAccessCountById(7L);
		assertTimerCount("srrrg.redirect", "outcome", "gone", 1);
	}

	@Test
	void doesNotRedirectWhenUrlChangesWhileItIsChecked() {
		Link oldLink = link("https://old.example");
		Link newLink = link("https://new.example");
		when(repository.findByCodeAndProjectIsNull("aB3x9Q"))
				.thenReturn(Optional.of(oldLink))
				.thenReturn(Optional.of(newLink));
		when(riskVerificationService.verify("https://old.example")).thenReturn(assessment(RiskVerdict.SAFE));
		when(repository.incrementAccessCountById(7L)).thenReturn(1);

		assertThatThrownBy(() -> service.redirect("srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(accessRecorder).record(eq(newLink), any(Instant.class), eq(Outcome.URL_CHANGED), eq(requestInfo));
		verify(repository, never()).incrementAccessAndRedirectCountsById(any());
		assertTimerCount("srrrg.redirect", "outcome", "check_failed", 1);
		assertWriteTimerCount("success", 1);
	}

	@Test
	void deletedLinkDoesNotCreateStatisticsEvent() {
		Link link = link("https://example.com");
		when(link.isDeleted()).thenReturn(true);
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.of(link));

		assertThatThrownBy(() -> service.redirect("srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(LinkGoneException.class)
				.extracting(exception -> ((LinkGoneException) exception).getReason())
				.isEqualTo(LinkGoneException.Reason.DELETED);
		verify(accessRecorder, never()).record(eq(link), any(), any(), eq(requestInfo));
		verify(repository, never()).incrementAccessCountById(any());
	}

	@Test
	void missingLinkRecordsNotFoundOutcome() {
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.redirect("srrrg.link", "aB3x9Q", requestInfo))
				.isInstanceOf(link.srrrg.link.LinkNotFoundException.class);

		assertTimerCount("srrrg.redirect", "outcome", "not_found", 1);
	}

	private Link availableLink(String url) {
		Link link = link(url);
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.of(link));
		return link;
	}

	private Link link(String url) {
		Link link = mock(Link.class);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getId()).thenReturn(7L);
		when(link.getOriginalUrl()).thenReturn(url);
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(false);
		return link;
	}

	private UrlRiskAssessment assessment(RiskVerdict verdict) {
		Instant verifiedAt = Instant.now();
		return new UrlRiskAssessment(verdict, verifiedAt, verifiedAt.plusSeconds(300));
	}

	private void assertWriteTimerCount(String outcome, long count) {
		assertThat(registry.get("srrrg.redirect.write")
				.tag("type", "access")
				.tag("outcome", outcome)
				.timer()
				.count()).isEqualTo(count);
	}

	private void assertTimerCount(String name, String tag, String value, long count) {
		assertThat(registry.get(name)
				.tag(tag, value)
				.timer()
				.count()).isEqualTo(count);
	}
}

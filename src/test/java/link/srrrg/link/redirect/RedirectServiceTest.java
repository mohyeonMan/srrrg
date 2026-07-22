package link.srrrg.link.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import link.srrrg.link.Link;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.UrlValidator;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.LinkClickEventRecorder;
import link.srrrg.link.access.LinkRedirectEventRecorder;
import link.srrrg.link.risk.RiskVerdict;
import link.srrrg.link.risk.UrlRiskAssessment;
import link.srrrg.link.risk.UrlRiskVerificationService;

class RedirectServiceTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final UrlValidator validator = mock(UrlValidator.class);
	private final UrlRiskVerificationService riskVerificationService = mock(UrlRiskVerificationService.class);
	private final LinkClickEventRecorder clickRecorder = mock(LinkClickEventRecorder.class);
	private final LinkRedirectEventRecorder redirectRecorder = mock(LinkRedirectEventRecorder.class);
	private final TransactionOperations transactions = new TransactionOperations() {
		@Override
		public <T> T execute(TransactionCallback<T> action) {
			return action.doInTransaction(new SimpleTransactionStatus());
		}
	};
	private final ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "agent");
	private RedirectService service;

	@BeforeEach
	void setUp() {
		service = new RedirectService(repository, validator, riskVerificationService,
				clickRecorder, redirectRecorder, transactions);
	}

	@Test
	void springSelectsThePlatformTransactionManagerConstructor() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.registerBean(LinkRepository.class, () -> repository);
			context.registerBean(UrlValidator.class, () -> validator);
			context.registerBean(UrlRiskVerificationService.class, () -> riskVerificationService);
			context.registerBean(LinkClickEventRecorder.class, () -> clickRecorder);
			context.registerBean(LinkRedirectEventRecorder.class, () -> redirectRecorder);
			context.registerBean(PlatformTransactionManager.class,
					() -> mock(PlatformTransactionManager.class));
			context.registerBean(RedirectService.class);

			context.refresh();

			assertThat(context.getBean(RedirectService.class)).isNotNull();
		}
	}

	@Test
	void safeUrlRecordsRedirectAndReturnsOriginalUrl() {
		Link link = availableLink("https://example.com/path?q=1");
		when(riskVerificationService.verify(link.getOriginalUrl())).thenReturn(assessment(RiskVerdict.SAFE));
		when(repository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);
		when(repository.incrementRedirectCountByCode("aB3x9Q")).thenReturn(1);

		String redirectUrl = service.redirect("aB3x9Q", requestInfo);

		assertThat(redirectUrl).isEqualTo("https://example.com/path?q=1");
		verify(clickRecorder).record(link, requestInfo);
		verify(redirectRecorder).record(link, requestInfo);
	}

	@Test
	void threatUrlRecordsClickButDoesNotRecordRedirect() {
		availableLink("https://bad.example");
		when(riskVerificationService.verify("https://bad.example")).thenReturn(assessment(RiskVerdict.THREAT));
		when(repository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);

		assertThatThrownBy(() -> service.redirect("aB3x9Q", requestInfo))
				.isInstanceOf(UnsafeUrlException.class);
		verify(clickRecorder).record(any(Link.class), eq(requestInfo));
		verifyNoInteractions(redirectRecorder);
		verify(repository, never()).incrementRedirectCountByCode(any());
	}

	@Test
	void unknownUrlRecordsClickButDoesNotRecordRedirect() {
		availableLink("https://example.com");
		when(riskVerificationService.verify("https://example.com"))
				.thenReturn(UrlRiskAssessment.unknown(Instant.now()));
		when(repository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);

		assertThatThrownBy(() -> service.redirect("aB3x9Q", requestInfo))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(clickRecorder).record(any(Link.class), eq(requestInfo));
		verifyNoInteractions(redirectRecorder);
	}

	@Test
	void expiredLinkReportsExpiredReason() {
		Link link = link("https://example.com");
		when(link.isExpiredAt(any(Instant.class))).thenReturn(true);
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));

		assertThatThrownBy(() -> service.redirect("aB3x9Q", requestInfo))
				.isInstanceOf(LinkGoneException.class)
				.extracting(exception -> ((LinkGoneException) exception).getReason())
				.isEqualTo(LinkGoneException.Reason.EXPIRED);
	}

	@Test
	void doesNotRedirectWhenUrlChangesWhileItIsChecked() {
		Link oldLink = link("https://old.example");
		Link newLink = link("https://new.example");
		when(repository.findByCode("aB3x9Q"))
				.thenReturn(Optional.of(oldLink))
				.thenReturn(Optional.of(newLink));
		when(riskVerificationService.verify("https://old.example")).thenReturn(assessment(RiskVerdict.SAFE));
		when(repository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);

		assertThatThrownBy(() -> service.redirect("aB3x9Q", requestInfo))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(clickRecorder).record(oldLink, requestInfo);
		verifyNoInteractions(redirectRecorder);
	}

	private Link availableLink(String url) {
		Link link = link(url);
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		return link;
	}

	private Link link(String url) {
		Link link = mock(Link.class);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn(url);
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(false);
		return link;
	}

	private UrlRiskAssessment assessment(RiskVerdict verdict) {
		Instant verifiedAt = Instant.now();
		return new UrlRiskAssessment(verdict, verifiedAt, verifiedAt.plusSeconds(300));
	}
}

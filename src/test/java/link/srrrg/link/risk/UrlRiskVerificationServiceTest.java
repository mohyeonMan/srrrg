package link.srrrg.link.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import link.srrrg.link.risk.google.GoogleSafeBrowsingClient;

class UrlRiskVerificationServiceTest {

	private final UrlVerificationRepository repository = mock(UrlVerificationRepository.class);
	private final GoogleSafeBrowsingClient client = mock(GoogleSafeBrowsingClient.class);
	private final UrlRiskVerificationService service = new UrlRiskVerificationService(
			repository, client, new SimpleMeterRegistry());

	@Test
	void returnsFreshCachedAssessmentWithoutCallingGoogle() {
		UrlVerification cached = mock(UrlVerification.class);
		UrlRiskAssessment assessment = assessment(RiskVerdict.SAFE, 300);
		when(cached.matches("https://example.com")).thenReturn(true);
		when(cached.isFreshAt(org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(true);
		when(cached.toAssessment()).thenReturn(assessment);
		when(repository.findById(anyString())).thenReturn(Optional.of(cached));

		assertThat(service.verify("https://example.com")).isEqualTo(assessment);
		verify(client, never()).check(anyString());
	}

	@Test
	void refreshesExpiredCacheAndUpsertsTheResult() {
		UrlVerification expired = mock(UrlVerification.class);
		when(expired.matches("https://example.com")).thenReturn(true);
		when(expired.isFreshAt(org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(false);
		when(repository.findById(anyString())).thenReturn(Optional.of(expired));
		UrlRiskAssessment assessment = assessment(RiskVerdict.SAFE, 300);
		when(client.check("https://example.com")).thenReturn(assessment);

		assertThat(service.verify("https://example.com")).isEqualTo(assessment);
		verify(repository).upsert(
				anyString(),
				org.mockito.ArgumentMatchers.eq("https://example.com"),
				org.mockito.ArgumentMatchers.eq("SAFE"),
				org.mockito.ArgumentMatchers.eq(assessment.verifiedAt()),
				org.mockito.ArgumentMatchers.eq(assessment.expiresAt())
		);
	}

	@Test
	void doesNotCacheUnknownAssessment() {
		when(repository.findById(anyString())).thenReturn(Optional.empty());
		UrlRiskAssessment assessment = UrlRiskAssessment.unknown(Instant.now());
		when(client.check("https://example.com")).thenReturn(assessment);

		assertThat(service.verify("https://example.com").verdict()).isEqualTo(RiskVerdict.UNKNOWN);
		verify(repository, never()).upsert(
				anyString(), anyString(), anyString(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void concurrentChecksForTheSameUrlShareOneGoogleRequest() throws Exception {
		when(repository.findById(anyString())).thenReturn(Optional.empty());
		CountDownLatch requestStarted = new CountDownLatch(1);
		CountDownLatch releaseRequest = new CountDownLatch(1);
		UrlRiskAssessment assessment = assessment(RiskVerdict.SAFE, 300);
		when(client.check("https://example.com")).thenAnswer(invocation -> {
			requestStarted.countDown();
			releaseRequest.await(2, TimeUnit.SECONDS);
			return assessment;
		});
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> service.verify("https://example.com"));
			assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();
			var second = executor.submit(() -> service.verify("https://example.com"));
			verify(repository, org.mockito.Mockito.timeout(1000).atLeast(3)).findById(anyString());
			releaseRequest.countDown();

			assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo(assessment);
			assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo(assessment);
			verify(client).check("https://example.com");
		} finally {
			releaseRequest.countDown();
			executor.shutdownNow();
		}
	}

	private UrlRiskAssessment assessment(RiskVerdict verdict, long cacheSeconds) {
		Instant verifiedAt = Instant.now();
		return new UrlRiskAssessment(verdict, verifiedAt, verifiedAt.plusSeconds(cacheSeconds));
	}
}

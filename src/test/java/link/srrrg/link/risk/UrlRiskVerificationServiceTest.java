package link.srrrg.link.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import link.srrrg.link.risk.google.GoogleSafeBrowsingClient;

class UrlRiskVerificationServiceTest {

	private final UrlVerificationRepository repository = mock(UrlVerificationRepository.class);
	private final GoogleSafeBrowsingClient client = mock(GoogleSafeBrowsingClient.class);
	private final UrlRiskVerificationService service = new UrlRiskVerificationService(repository, client);

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
	void checksGoogleAndSavesResultWhenCachedVerificationHasExpired() {
		UrlVerification expired = mock(UrlVerification.class);
		when(expired.matches("https://example.com")).thenReturn(true);
		when(expired.isFreshAt(org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(false);
		when(repository.findById(anyString())).thenReturn(Optional.of(expired));
		UrlRiskAssessment assessment = assessment(RiskVerdict.SAFE, 300);
		when(client.check("https://example.com")).thenReturn(assessment);

		assertThat(service.verify("https://example.com")).isEqualTo(assessment);
		verify(repository).findById(anyString());
		verify(repository).saveIfNewer(
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
		verify(repository, never()).saveIfNewer(
				anyString(), anyString(), anyString(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private UrlRiskAssessment assessment(RiskVerdict verdict, long cacheSeconds) {
		Instant verifiedAt = Instant.now();
		return new UrlRiskAssessment(verdict, verifiedAt, verifiedAt.plusSeconds(cacheSeconds));
	}
}

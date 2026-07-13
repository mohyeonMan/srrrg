package link.srrrg.link.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkStatus;
import link.srrrg.link.risk.UrlRiskCheckResult;

class RedirectCheckRecorderTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final LinkClickEventRecorder clickRecorder = mock(LinkClickEventRecorder.class);
	private final LinkRedirectEventRecorder redirectRecorder = mock(LinkRedirectEventRecorder.class);
	private final RedirectCheckRecorder recorder = new RedirectCheckRecorder(repository, clickRecorder, redirectRecorder);
	private final ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "agent");

	@Test
	void recordsVerificationAndAccessForNoThreatResult() {
		Link link = link();
		when(repository.updateVerificationByCodeAndOriginalUrl(
				org.mockito.ArgumentMatchers.eq("aB3x9Q"), org.mockito.ArgumentMatchers.eq("https://example.com"),
				org.mockito.ArgumentMatchers.eq(LinkStatus.NO_THREAT_FOUND), any(Instant.class))).thenReturn(1);
		when(repository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);
		when(repository.incrementRedirectCountByCode("aB3x9Q")).thenReturn(1);

		assertThat(recorder.recordCheck(link, "https://example.com",
				UrlRiskCheckResult.NO_THREAT_FOUND, requestInfo)).isTrue();

		verify(clickRecorder).record(link, requestInfo);
		verify(redirectRecorder).record(link, requestInfo);
	}

	@Test
	void recordsThreatWithoutCountingAccess() {
		Link link = link();
		when(repository.updateVerificationByCodeAndOriginalUrl(
				org.mockito.ArgumentMatchers.eq("aB3x9Q"), org.mockito.ArgumentMatchers.eq("https://example.com"),
				org.mockito.ArgumentMatchers.eq(LinkStatus.THREAT_DETECTED), any(Instant.class))).thenReturn(1);

		assertThat(recorder.recordCheck(link, "https://example.com",
				UrlRiskCheckResult.THREAT_DETECTED, requestInfo)).isTrue();
		verify(clickRecorder, never()).record(any(), any());
		verify(redirectRecorder, never()).record(any(), any());
		verify(repository, never()).incrementClickCountByCode(any());
	}

	@Test
	void rejectsAResultWhenTheStoredUrlChanged() {
		Link link = link();
		when(repository.updateVerificationByCodeAndOriginalUrl(any(), any(), any(), any())).thenReturn(0);

		assertThat(recorder.recordCheck(link, "https://old.example",
				UrlRiskCheckResult.NO_THREAT_FOUND, requestInfo)).isFalse();
		verify(clickRecorder, never()).record(any(), any());
		verify(repository, never()).incrementClickCountByCode(any());
	}

	@Test
	void reusesCachedNoThreatResultAndRecordsAccessWithoutChangingVerifiedAt() {
		Link link = link();
		Instant verifiedAt = Instant.now().minusSeconds(600);
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.getOriginalUrl()).thenReturn("https://example.com");
		when(link.getStatus()).thenReturn(LinkStatus.NO_THREAT_FOUND);
		when(link.getVerifiedAt()).thenReturn(verifiedAt);
		when(repository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);
		when(repository.incrementRedirectCountByCode("aB3x9Q")).thenReturn(1);

		assertThat(recorder.reuseCachedCheck("aB3x9Q", "https://example.com",
				LinkStatus.NO_THREAT_FOUND, verifiedAt, requestInfo)).contains(LinkStatus.NO_THREAT_FOUND);
		verify(link, never()).updateVerification(any(), any());
		verify(clickRecorder).record(link, requestInfo);
	}

	private Link link() {
		Link link = mock(Link.class);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(false);
		return link;
	}
}

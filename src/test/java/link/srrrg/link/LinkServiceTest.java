package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.RedirectCheckRecorder;
import link.srrrg.link.dto.CreateLinkRequest;
import link.srrrg.link.dto.RedirectCheckResponse;
import link.srrrg.link.dto.UpdateLinkRequest;
import link.srrrg.link.risk.UrlRiskCheckResult;
import link.srrrg.link.risk.UrlRiskChecker;

class LinkServiceTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final LinkCodeGenerator codeGenerator = mock(LinkCodeGenerator.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final UrlValidator validator = mock(UrlValidator.class);
	private final UrlRiskChecker riskChecker = mock(UrlRiskChecker.class);
	private final RedirectCheckRecorder checkRecorder = mock(RedirectCheckRecorder.class);
	private final ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "agent");
	private LinkService service;

	@BeforeEach
	void setUp() {
		service = new LinkService(repository, codeGenerator, secretKeyManager, validator,
				riskChecker, checkRecorder, "https://srrrg.link/", Duration.ofHours(1));
	}

	@Test
	void createsUrlWhenNoThreatIsFound() {
		when(riskChecker.check("https://example.com/path?q=1")).thenReturn(UrlRiskCheckResult.NO_THREAT_FOUND);
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(repository.existsByCode("aB3x9Q")).thenReturn(false);
		when(secretKeyManager.generate()).thenReturn(new GeneratedSecretKey("plain", "hash"));
		when(repository.save(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		var response = service.create(new CreateLinkRequest("https://example.com/path?q=1", null));

		assertThat(response.code()).isEqualTo("aB3x9Q");
		verify(validator).validate("https://example.com/path?q=1");
		verify(repository).save(org.mockito.ArgumentMatchers.argThat(link ->
				link.getStatus() == LinkStatus.NO_THREAT_FOUND && link.getVerifiedAt() != null));
	}

	@Test
	void rejectsCreationWhenThreatIsDetected() {
		when(riskChecker.check(any())).thenReturn(UrlRiskCheckResult.THREAT_DETECTED);
		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://bad.example", null)))
				.isInstanceOf(UnsafeUrlException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsCreationWhenCheckFails() {
		when(riskChecker.check(any())).thenReturn(UrlRiskCheckResult.CHECK_FAILED);
		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null)))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void updatesChangedUrlOnlyAfterNoThreatResult() {
		Link link = managedLink("https://old.example");
		when(riskChecker.check("https://new.example/path")).thenReturn(UrlRiskCheckResult.NO_THREAT_FOUND);
		when(repository.save(link)).thenReturn(link);
		UpdateLinkRequest request = updateUrl("https://new.example/path");

		service.updateManagedLink("aB3x9Q", "secret", request);

		verify(link).updateOriginalUrl("https://new.example/path");
		verify(link).updateVerification(org.mockito.ArgumentMatchers.eq(LinkStatus.NO_THREAT_FOUND), any(Instant.class));
	}

	@Test
	void keepsExistingUrlWhenChangedUrlHasThreat() {
		Link link = managedLink("https://old.example");
		when(riskChecker.check(any())).thenReturn(UrlRiskCheckResult.THREAT_DETECTED);
		assertThatThrownBy(() -> service.updateManagedLink("aB3x9Q", "secret", updateUrl("https://bad.example")))
				.isInstanceOf(UnsafeUrlException.class);
		verify(link, never()).updateOriginalUrl(any());
		verify(repository, never()).save(any());
	}

	@Test
	void keepsExistingUrlWhenChangedUrlCheckFails() {
		Link link = managedLink("https://old.example");
		when(riskChecker.check(any())).thenReturn(UrlRiskCheckResult.CHECK_FAILED);
		assertThatThrownBy(() -> service.updateManagedLink("aB3x9Q", "secret", updateUrl("https://new.example")))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(link, never()).updateOriginalUrl(any());
		verify(repository, never()).save(any());
	}

	@Test
	void skipsRiskCheckWhenUrlIsUnchanged() {
		Link link = managedLink("https://same.example");
		when(repository.save(link)).thenReturn(link);
		service.updateManagedLink("aB3x9Q", "secret", updateUrl("https://same.example"));
		verify(riskChecker, never()).check(any());
		verify(link, never()).updateOriginalUrl(any());
	}

	@Test
	void pageResolutionDoesNotRecordAVisit() {
		Link link = availableLink("https://example.com/path");
		var result = service.resolveRedirectPage("aB3x9Q", requestInfo);
		assertThat(result.originalUrl()).isEqualTo("https://example.com/path");
		verify(checkRecorder, never()).recordCheck(any(), any(), any(), any());
	}

	@Test
	void successfulCheckReturnsIdenticalUrlAndRecordsExactlyOnce() {
		Link link = availableLink("https://example.com:8443/path?q=1");
		when(riskChecker.check(link.getOriginalUrl())).thenReturn(UrlRiskCheckResult.NO_THREAT_FOUND);

		RedirectCheckResponse response = service.checkRedirect("aB3x9Q", requestInfo);

		assertThat(response.status()).isEqualTo(UrlRiskCheckResult.NO_THREAT_FOUND);
		assertThat(response.redirectUrl()).isEqualTo("https://example.com:8443/path?q=1");
		verify(checkRecorder).recordCheck(link, "https://example.com:8443/path?q=1",
				UrlRiskCheckResult.NO_THREAT_FOUND, requestInfo);
	}

	@Test
	void threatResponseHasNoRedirectUrlAndDoesNotRecordVisit() {
		availableLink("https://bad.example");
		when(riskChecker.check(any())).thenReturn(UrlRiskCheckResult.THREAT_DETECTED);
		RedirectCheckResponse response = service.checkRedirect("aB3x9Q", requestInfo);
		assertThat(response.redirectUrl()).isNull();
		verify(checkRecorder).recordCheck(any(), org.mockito.ArgumentMatchers.eq("https://bad.example"),
				org.mockito.ArgumentMatchers.eq(UrlRiskCheckResult.THREAT_DETECTED), any());
	}

	@Test
	void failedCheckReturnsRevalidatedUrlWithoutRecordingVisit() {
		availableLink("https://example.com/path");
		when(riskChecker.check(any())).thenReturn(UrlRiskCheckResult.CHECK_FAILED);
		RedirectCheckResponse response = service.checkRedirect("aB3x9Q", requestInfo);
		assertThat(response.redirectUrl()).isEqualTo("https://example.com/path");
		verify(checkRecorder).recordCheck(any(), org.mockito.ArgumentMatchers.eq("https://example.com/path"),
				org.mockito.ArgumentMatchers.eq(UrlRiskCheckResult.CHECK_FAILED), any());
	}

	@Test
	void doesNotReturnUrlThatChangedWhileItWasChecked() {
		Link oldLink = link("https://old.example");
		Link newLink = link("https://new.example");
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.of(oldLink), Optional.of(newLink));
		when(riskChecker.check(any())).thenReturn(UrlRiskCheckResult.NO_THREAT_FOUND);
		RedirectCheckResponse response = service.checkRedirect("aB3x9Q", requestInfo);
		assertThat(response.status()).isEqualTo(UrlRiskCheckResult.CHECK_FAILED);
		assertThat(response.redirectUrl()).isNull();
		verify(checkRecorder, never()).recordCheck(any(), any(), any(), any());
	}

	@Test
	void reusesVerificationWithinOneHourWithoutCallingRiskChecker() {
		Link link = availableLink("https://example.com/path");
		Instant verifiedAt = Instant.now().minusSeconds(1800);
		when(link.getStatus()).thenReturn(LinkStatus.NO_THREAT_FOUND);
		when(link.getVerifiedAt()).thenReturn(verifiedAt);
		when(checkRecorder.reuseCachedCheck("aB3x9Q", "https://example.com/path",
				LinkStatus.NO_THREAT_FOUND, verifiedAt, requestInfo))
				.thenReturn(Optional.of(LinkStatus.NO_THREAT_FOUND));

		var response = service.resolveRedirectPage("aB3x9Q", requestInfo);

		assertThat(response.cachedStatus()).isEqualTo(LinkStatus.NO_THREAT_FOUND);
		verify(riskChecker, never()).check(any());
		verify(checkRecorder).reuseCachedCheck("aB3x9Q", "https://example.com/path",
				LinkStatus.NO_THREAT_FOUND, verifiedAt, requestInfo);
	}

	@Test
	void doesNotReuseVerificationOlderThanOneHour() {
		Link link = availableLink("https://example.com/path");
		when(link.getStatus()).thenReturn(LinkStatus.NO_THREAT_FOUND);
		when(link.getVerifiedAt()).thenReturn(Instant.now().minusSeconds(3601));

		var response = service.resolveRedirectPage("aB3x9Q", requestInfo);

		assertThat(response.cachedStatus()).isNull();
		verify(checkRecorder, never()).reuseCachedCheck(any(), any(), any(), any(), any());
	}

	private Link managedLink(String url) {
		Link link = link(url);
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeyManager.matches("secret", "hash")).thenReturn(true);
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		return link;
	}

	private Link availableLink(String url) {
		Link link = link(url);
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(checkRecorder.recordCheck(any(), any(), any(), any())).thenReturn(true);
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

	private UpdateLinkRequest updateUrl(String url) {
		UpdateLinkRequest request = new UpdateLinkRequest();
		request.setOriginalUrl(url);
		return request;
	}
}

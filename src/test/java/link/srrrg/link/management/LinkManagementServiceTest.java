package link.srrrg.link.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import link.srrrg.link.Link;
import link.srrrg.link.LinkCodeGenerator;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkStatus;
import link.srrrg.link.SecretKeyManager;
import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.UrlValidator;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import link.srrrg.link.risk.UrlRiskCheckResult;
import link.srrrg.link.risk.UrlRiskChecker;

class LinkManagementServiceTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final LinkCodeGenerator codeGenerator = mock(LinkCodeGenerator.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final UrlValidator validator = mock(UrlValidator.class);
	private final UrlRiskChecker riskChecker = mock(UrlRiskChecker.class);
	private LinkManagementService service;

	@BeforeEach
	void setUp() {
		service = new LinkManagementService(repository, codeGenerator, secretKeyManager, validator,
				riskChecker, "https://srrrg.link/");
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
	void rejectsMissingManagedLink() {
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getManagedLink("aB3x9Q", "secret"))
				.isInstanceOf(LinkNotFoundException.class);
	}

	@Test
	void rejectsDeletedManagedLink() {
		Link link = managedLink("https://example.com");
		when(link.isDeleted()).thenReturn(true);

		assertThatThrownBy(() -> service.getManagedLink("aB3x9Q", "secret"))
				.isInstanceOf(LinkGoneException.class);
	}

	private Link managedLink(String url) {
		Link link = link(url);
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeyManager.matches("secret", "hash")).thenReturn(true);
		when(repository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		return link;
	}

	private Link link(String url) {
		Link link = mock(Link.class);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn(url);
		when(link.getSecretKeyHash()).thenReturn("link-secret-hash");
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

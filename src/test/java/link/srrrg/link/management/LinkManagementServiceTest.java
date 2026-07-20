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
import link.srrrg.link.SecretKeyManager;
import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.UnsafeUrlException;
import link.srrrg.link.UrlRiskCheckFailedException;
import link.srrrg.link.UrlValidator;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import link.srrrg.link.risk.RiskVerdict;
import link.srrrg.link.risk.UrlRiskAssessment;
import link.srrrg.link.risk.UrlRiskVerificationService;

class LinkManagementServiceTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final LinkCodeGenerator codeGenerator = mock(LinkCodeGenerator.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final UrlValidator validator = mock(UrlValidator.class);
	private final UrlRiskVerificationService riskVerificationService = mock(UrlRiskVerificationService.class);
	private LinkManagementService service;

	@BeforeEach
	void setUp() {
		service = new LinkManagementService(repository, codeGenerator, secretKeyManager, validator,
				riskVerificationService, "https://srrrg.link/");
	}

	@Test
	void createsUrlWhenNoThreatIsFound() {
		when(riskVerificationService.verify("https://example.com/path?q=1"))
				.thenReturn(assessment(RiskVerdict.SAFE));
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(repository.existsByCode("aB3x9Q")).thenReturn(false);
		when(secretKeyManager.generate()).thenReturn(new GeneratedSecretKey("plain", "hash"));
		when(repository.save(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		var response = service.create(new CreateLinkRequest("https://example.com/path?q=1", null));

		assertThat(response.code()).isEqualTo("aB3x9Q");
		verify(validator).validate("https://example.com/path?q=1");
		verify(repository).save(any(Link.class));
	}

	@Test
	void rejectsCreationWhenThreatIsDetected() {
		when(riskVerificationService.verify(any())).thenReturn(assessment(RiskVerdict.THREAT));
		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://bad.example", null)))
				.isInstanceOf(UnsafeUrlException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsCreationWhenCheckFails() {
		when(riskVerificationService.verify(any())).thenReturn(UrlRiskAssessment.unknown(Instant.now()));
		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null)))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void updatesChangedUrlOnlyAfterNoThreatResult() {
		Link link = managedLink("https://old.example");
		when(riskVerificationService.verify("https://new.example/path"))
				.thenReturn(assessment(RiskVerdict.SAFE));
		when(repository.save(link)).thenReturn(link);
		UpdateLinkRequest request = updateUrl("https://new.example/path");

		service.updateManagedLink("aB3x9Q", "secret", request);

		verify(link).updateOriginalUrl("https://new.example/path");
	}

	@Test
	void keepsExistingUrlWhenChangedUrlHasThreat() {
		Link link = managedLink("https://old.example");
		when(riskVerificationService.verify(any())).thenReturn(assessment(RiskVerdict.THREAT));
		assertThatThrownBy(() -> service.updateManagedLink("aB3x9Q", "secret", updateUrl("https://bad.example")))
				.isInstanceOf(UnsafeUrlException.class);
		verify(link, never()).updateOriginalUrl(any());
		verify(repository, never()).save(any());
	}

	@Test
	void keepsExistingUrlWhenChangedUrlCheckFails() {
		Link link = managedLink("https://old.example");
		when(riskVerificationService.verify(any())).thenReturn(UrlRiskAssessment.unknown(Instant.now()));
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
		verify(riskVerificationService, never()).verify(any());
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

	private UrlRiskAssessment assessment(RiskVerdict verdict) {
		Instant verifiedAt = Instant.now();
		return new UrlRiskAssessment(verdict, verifiedAt, verifiedAt.plusSeconds(300));
	}
}

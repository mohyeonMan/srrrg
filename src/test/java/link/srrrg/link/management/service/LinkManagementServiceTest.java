package link.srrrg.link.management.service;

import link.srrrg.link.creation.service.LinkCodeGenerator;
import link.srrrg.link.management.service.LinkManagementService;
import link.srrrg.link.repository.LinkRepository;
import link.srrrg.link.repository.LinkUtmValueRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.link.model.Link;
import link.srrrg.link.address.service.LinkAddressService;
import link.srrrg.link.creation.model.LinkIdempotencyConflictException;
import link.srrrg.link.creation.service.LinkCreationService;
import link.srrrg.link.creation.service.LinkCodeGenerator;
import link.srrrg.link.model.LinkNotFoundException;
import link.srrrg.link.repository.LinkRepository;
import link.srrrg.link.repository.LinkUtmValueRepository;
import link.srrrg.link.anonymous.service.SecretKeyManager;
import link.srrrg.link.anonymous.service.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.risk.model.UnsafeUrlException;
import link.srrrg.link.risk.model.UrlRiskCheckFailedException;
import link.srrrg.link.creation.service.UrlValidator;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import link.srrrg.link.risk.model.RiskVerdict;
import link.srrrg.link.risk.model.UrlRiskAssessment;
import link.srrrg.link.risk.service.UrlRiskVerificationService;
import link.srrrg.identity.account.model.User;
import link.srrrg.project.model.Project;
import link.srrrg.campaign.model.Campaign;

class LinkManagementServiceTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final LinkUtmValueRepository linkUtmValueRepository = mock(LinkUtmValueRepository.class);
	private final LinkCodeGenerator codeGenerator = mock(LinkCodeGenerator.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final UrlValidator validator = mock(UrlValidator.class);
	private final UrlRiskVerificationService riskVerificationService = mock(UrlRiskVerificationService.class);
	private SimpleMeterRegistry registry;
	private LinkManagementService service;
	private LinkCreationService creationService;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		SrrrgMetrics metrics = new SrrrgMetrics(registry);
		creationService = new LinkCreationService(repository, linkUtmValueRepository, codeGenerator, validator, metrics);
		service = new LinkManagementService(repository, codeGenerator, secretKeyManager, validator,
				riskVerificationService, metrics, new LinkAddressService("https://srrrg.link/"), "https://srrrg.link/");
	}

	@Test
	void createsUrlWhenNoThreatIsFound() {
		when(riskVerificationService.verify("https://example.com/path?q=1"))
				.thenReturn(assessment(RiskVerdict.SAFE));
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(secretKeyManager.generate()).thenReturn(new GeneratedSecretKey("plain", "hash"));
		when(repository.saveAndFlush(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		var response = service.create(new CreateLinkRequest("https://example.com/path?q=1", null));

		assertThat(response.code()).isEqualTo("aB3x9Q");
		verify(validator).validate("https://example.com/path?q=1");
		verify(repository).saveAndFlush(any(Link.class));
		assertCreateTimerCount("created", 1);
	}

	@Test
	void retriesCreationWhenGeneratedCodeAlreadyExists() {
		when(riskVerificationService.verify("https://example.com"))
				.thenReturn(assessment(RiskVerdict.SAFE));
		when(codeGenerator.generate()).thenReturn("aaaaaa", "bbbbbb");
		when(secretKeyManager.generate()).thenReturn(new GeneratedSecretKey("plain", "hash"));
		when(repository.saveAndFlush(any(Link.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate code"))
				.thenAnswer(call -> call.getArgument(0));

		var response = service.create(new CreateLinkRequest("https://example.com", null));

		assertThat(response.code()).isEqualTo("bbbbbb");
		verify(repository, times(2)).saveAndFlush(any(Link.class));
		assertThat(registry.get("srrrg.link.code_generation")
				.tag("outcome", "collision")
				.counter()
				.count()).isEqualTo(1);
	}

	@Test
	void createsProjectLinkWithoutSecret() {
		Project project = mock(Project.class);
		User user = mock(User.class);
		when(project.activeSubdomain()).thenReturn("acme");
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(repository.saveAndFlush(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		Link link = creationService.createForProject(new CreateLinkRequest("https://example.com", null, "  프로모션  "), project, user);

		assertThat(link.getProject()).isSameAs(project);
		assertThat(link.getSubdomain()).isEqualTo("acme");
		assertThat(link.getCreatedBy()).isSameAs(user);
		assertThat(link.getName()).isEqualTo("프로모션");
		assertThat(link.getSecretKeyHash()).isNull();
		verify(riskVerificationService, never()).verify(any());
	}

	@Test
	void snapshotsOnlyAnEnabledSubdomainOnTheLink() {
		Project project = Project.create("Acme", null, null);
		when(codeGenerator.generate()).thenReturn("aB3x9Q", "cD4y8R");
		when(repository.saveAndFlush(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		project.claimSubdomain("acme");
		Link baseLink = creationService.createForProject(new CreateLinkRequest("https://base.example", null), project, null);
		project.setSubdomainEnabled(true);
		Link subdomainLink = creationService.createForProject(new CreateLinkRequest("https://sub.example", null), project, null);
		project.releaseSubdomain();

		assertThat(baseLink.getSubdomain()).isNull();
		assertThat(subdomainLink.getSubdomain()).isEqualTo("acme");
	}

	@Test
	void createsCampaignLinkWithoutOwnUrlAndWithoutRiskCheck() {
		Project project = mock(Project.class);
		Campaign campaign = mock(Campaign.class);
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(repository.saveAndFlush(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		Link link = creationService.createForCampaign(null, null, project, null, null, null, null,
				campaign, null, null, Map.of());

		assertThat(link.getOriginalUrl()).isNull();
		assertThat(link.getCampaign()).isSameAs(campaign);
		verify(validator, never()).validate(any());
		verify(riskVerificationService, never()).verify(any());
	}

	@Test
	void returnsSameProjectLinkForSameIdempotencyRequest() {
		Link existing = mock(Link.class);
		when(existing.getIdempotencyRequestHash()).thenReturn("request-hash");
		when(repository.findByIdempotencyApiKeyIdAndIdempotencyKey(3L, "retry-1"))
				.thenReturn(Optional.of(existing));

		Link link = creationService.createForProject(new CreateLinkRequest("https://example.com", null),
				mock(Project.class), null, 3L, "retry-1", "request-hash");

		assertThat(link).isSameAs(existing);
		verify(validator, never()).validate(any());
	}

	@Test
	void rejectsReusedIdempotencyKeyForDifferentRequest() {
		Link existing = mock(Link.class);
		when(existing.getIdempotencyRequestHash()).thenReturn("first-hash");
		when(repository.findByIdempotencyApiKeyIdAndIdempotencyKey(3L, "retry-1"))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> creationService.createForProject(new CreateLinkRequest("https://other.example", null),
				mock(Project.class), null, 3L, "retry-1", "second-hash"))
				.isInstanceOf(LinkIdempotencyConflictException.class);
	}

	@Test
	void resolvesConcurrentIdempotencyInsertToExistingLink() {
		Link existing = mock(Link.class);
		when(existing.getIdempotencyRequestHash()).thenReturn("request-hash");
		when(repository.findByIdempotencyApiKeyIdAndIdempotencyKey(3L, "retry-1"))
				.thenReturn(Optional.empty(), Optional.of(existing));
		when(riskVerificationService.verify(any())).thenReturn(assessment(RiskVerdict.SAFE));
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(repository.saveAndFlush(any(Link.class))).thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

		Link link = creationService.createForProject(new CreateLinkRequest("https://example.com", null),
				mock(Project.class), null, 3L, "retry-1", "request-hash");

		assertThat(link).isSameAs(existing);
		verify(repository, times(1)).saveAndFlush(any());
	}

	@Test
	void rejectsCreationWhenThreatIsDetected() {
		when(riskVerificationService.verify(any())).thenReturn(assessment(RiskVerdict.THREAT));
		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://bad.example", null)))
				.isInstanceOf(UnsafeUrlException.class);
		verify(repository, never()).saveAndFlush(any());
		assertCreateTimerCount("threat", 1);
	}

	@Test
	void rejectsCreationWhenCheckFails() {
		when(riskVerificationService.verify(any())).thenReturn(UrlRiskAssessment.unknown(Instant.now()));
		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null)))
				.isInstanceOf(UrlRiskCheckFailedException.class);
		verify(repository, never()).saveAndFlush(any());
		assertCreateTimerCount("check_failed", 1);
	}

	@Test
	void recordsInvalidCreationOutcome() {
		org.mockito.Mockito.doThrow(new IllegalArgumentException("invalid"))
				.when(validator).validate("not-a-url");

		assertThatThrownBy(() -> service.create(new CreateLinkRequest("not-a-url", null)))
				.isInstanceOf(IllegalArgumentException.class);

		assertCreateTimerCount("invalid", 1);
	}

	@Test
	void recordsExhaustedCodeGenerationRetries() {
		when(riskVerificationService.verify("https://example.com"))
				.thenReturn(assessment(RiskVerdict.SAFE));
		when(codeGenerator.generate()).thenReturn("aaaaaa");
		when(secretKeyManager.generate()).thenReturn(new GeneratedSecretKey("plain", "hash"));
		when(repository.saveAndFlush(any(Link.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate code"));

		assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null)))
				.isInstanceOf(IllegalStateException.class);

		assertThat(registry.get("srrrg.link.code_generation")
				.tag("outcome", "collision")
				.counter()
				.count()).isEqualTo(5);
		assertThat(registry.get("srrrg.link.code_generation")
				.tag("outcome", "exhausted")
				.counter()
				.count()).isEqualTo(1);
		assertCreateTimerCount("error", 1);
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
	void authenticatedCampaignLinkCanReturnToInheritedDestinationWithoutRiskCheck() {
		Link link = link("https://own.example");
		when(link.getSubdomain()).thenReturn("acme");
		when(link.getCampaign()).thenReturn(mock(Campaign.class));
		when(repository.save(link)).thenReturn(link);
		UpdateLinkRequest request = new UpdateLinkRequest();
		request.setOriginalUrl(null);

		var response = service.updateProjectLink(link, request);

		verify(link).updateOriginalUrl(null);
		verify(riskVerificationService, never()).verify(any());
		assertThat(response.shortUrl()).isEqualTo("https://acme.srrrg.link/aB3x9Q");
	}

	@Test
	void standaloneProjectLinkCannotRemoveItsDestination() {
		Link link = link("https://own.example");
		UpdateLinkRequest request = new UpdateLinkRequest();
		request.setOriginalUrl(null);

		assertThatThrownBy(() -> service.updateProjectLink(link, request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("프로젝트 단일 링크에는 목적지 URL이 필요합니다.");
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsMissingManagedLink() {
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getManagedLink("aB3x9Q", "secret"))
				.isInstanceOf(LinkNotFoundException.class);
	}

	@Test
	void rejectsDeletedManagedLinkAsNotFound() {
		// @SoftDelete가 조회 단계에서 걸러내므로 삭제된 링크는 410이 아니라 404가 된다.
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getManagedLink("aB3x9Q", "secret"))
				.isInstanceOf(LinkNotFoundException.class);
	}

	private Link managedLink(String url) {
		Link link = link(url);
		when(link.getSecretKeyHash()).thenReturn("hash");
		when(secretKeyManager.matches("secret", "hash")).thenReturn(true);
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.of(link));
		return link;
	}

	private Link link(String url) {
		Link link = mock(Link.class);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn(url);
		when(link.getSecretKeyHash()).thenReturn("link-secret-hash");
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

	private void assertCreateTimerCount(String outcome, long count) {
		assertThat(registry.get("srrrg.link.create")
				.tag("outcome", outcome)
				.timer()
				.count()).isEqualTo(count);
	}
}

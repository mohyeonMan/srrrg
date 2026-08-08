package link.srrrg.link.management;

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
import link.srrrg.domain.ProjectDomain;
import link.srrrg.link.Link;
import link.srrrg.link.LinkCodeGenerator;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkUtmValueRepository;
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
import link.srrrg.identity.User;
import link.srrrg.project.Project;
import link.srrrg.campaign.Campaign;

class LinkManagementServiceTest {

	private final LinkRepository repository = mock(LinkRepository.class);
	private final LinkUtmValueRepository linkUtmValueRepository = mock(LinkUtmValueRepository.class);
	private final LinkCodeGenerator codeGenerator = mock(LinkCodeGenerator.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final UrlValidator validator = mock(UrlValidator.class);
	private final UrlRiskVerificationService riskVerificationService = mock(UrlRiskVerificationService.class);
	private SimpleMeterRegistry registry;
	private LinkManagementService service;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		service = new LinkManagementService(repository, linkUtmValueRepository, codeGenerator, secretKeyManager, validator,
				riskVerificationService, new SrrrgMetrics(registry), "https://srrrg.link/");
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
		ProjectDomain domain = mock(ProjectDomain.class);
		User user = mock(User.class);
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(repository.saveAndFlush(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		Link link = service.createForProject(new CreateLinkRequest("https://example.com", null), project, domain, user);

		assertThat(link.getProject()).isSameAs(project);
		assertThat(link.getDomain()).isSameAs(domain);
		assertThat(link.getCreatedBy()).isSameAs(user);
		assertThat(link.getSecretKeyHash()).isNull();
		verify(riskVerificationService, never()).verify(any());
	}

	@Test
	void createsCampaignLinkWithoutOwnUrlAndWithoutRiskCheck() {
		Project project = mock(Project.class);
		ProjectDomain domain = mock(ProjectDomain.class);
		Campaign campaign = mock(Campaign.class);
		when(codeGenerator.generate()).thenReturn("aB3x9Q");
		when(repository.saveAndFlush(any(Link.class))).thenAnswer(call -> call.getArgument(0));

		Link link = service.createForCampaign(null, null, project, domain, null, null, null, null,
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

		Link link = service.createForProject(new CreateLinkRequest("https://example.com", null),
				mock(Project.class), mock(ProjectDomain.class), null, 3L, "retry-1", "request-hash");

		assertThat(link).isSameAs(existing);
		verify(validator, never()).validate(any());
	}

	@Test
	void rejectsReusedIdempotencyKeyForDifferentRequest() {
		Link existing = mock(Link.class);
		when(existing.getIdempotencyRequestHash()).thenReturn("first-hash");
		when(repository.findByIdempotencyApiKeyIdAndIdempotencyKey(3L, "retry-1"))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.createForProject(new CreateLinkRequest("https://other.example", null),
				mock(Project.class), mock(ProjectDomain.class), null, 3L, "retry-1", "second-hash"))
				.isInstanceOf(LinkManagementService.IdempotencyConflictException.class);
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

		Link link = service.createForProject(new CreateLinkRequest("https://example.com", null),
				mock(Project.class), mock(ProjectDomain.class), null, 3L, "retry-1", "request-hash");

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
		ProjectDomain domain = mock(ProjectDomain.class);
		when(domain.getHostname()).thenReturn("acme.srrrg.link");
		when(link.getDomain()).thenReturn(domain);
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
		when(repository.findByCodeAndProjectIsNull("aB3x9Q")).thenReturn(Optional.of(link));
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

	private void assertCreateTimerCount(String outcome, long count) {
		assertThat(registry.get("srrrg.link.create")
				.tag("outcome", outcome)
				.timer()
				.count()).isEqualTo(count);
	}
}

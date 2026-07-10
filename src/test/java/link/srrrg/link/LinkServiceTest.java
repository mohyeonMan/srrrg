package link.srrrg.link;

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

import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.LinkClickEventRecorder;
import link.srrrg.link.access.LinkRedirectEventRecorder;
import link.srrrg.link.dto.CreateLinkRequest;
import link.srrrg.link.dto.CreateLinkResponse;
import link.srrrg.link.dto.DeleteLinkResponse;
import link.srrrg.link.dto.LinkManagementResponse;
import link.srrrg.link.dto.RedirectLink;
import link.srrrg.link.dto.UpdateLinkRequest;

class LinkServiceTest {

	private final LinkRepository linkRepository = mock(LinkRepository.class);
	private final LinkCodeGenerator linkCodeGenerator = mock(LinkCodeGenerator.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final UrlValidator urlValidator = mock(UrlValidator.class);
	private final LinkClickEventRecorder clickEventRecorder = mock(LinkClickEventRecorder.class);
	private final LinkRedirectEventRecorder redirectEventRecorder = mock(LinkRedirectEventRecorder.class);
	private final ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "test-agent");

	private LinkService linkService;

	@BeforeEach
	void setUp() {
		linkService = new LinkService(
				linkRepository,
				linkCodeGenerator,
				secretKeyManager,
				urlValidator,
				clickEventRecorder,
				redirectEventRecorder,
				"https://srrrg.link/"
		);
	}

	@Test
	void createsLinkAndReturnsPlainSecretOnce() {
		Instant expiresAt = Instant.now().plusSeconds(3600);
		when(linkCodeGenerator.generate()).thenReturn("aB3x9Q");
		when(linkRepository.existsByCode("aB3x9Q")).thenReturn(false);
		when(secretKeyManager.generate()).thenReturn(new GeneratedSecretKey("srrrg_sk_plain", "bcrypt-hash"));
		when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CreateLinkResponse response = linkService.create(
				new CreateLinkRequest("https://example.com", expiresAt)
		);

		assertThat(response.code()).isEqualTo("aB3x9Q");
		assertThat(response.shortUrl()).isEqualTo("https://srrrg.link/aB3x9Q");
		assertThat(response.secretKey()).isEqualTo("srrrg_sk_plain");
		assertThat(response.expiresAt()).isEqualTo(expiresAt);
		verify(urlValidator).validate("https://example.com");
	}

	@Test
	void retriesWhenGeneratedCodeAlreadyExists() {
		when(linkCodeGenerator.generate()).thenReturn("aaaaaa", "bbbbbb");
		when(linkRepository.existsByCode("aaaaaa")).thenReturn(true);
		when(linkRepository.existsByCode("bbbbbb")).thenReturn(false);
		when(secretKeyManager.generate()).thenReturn(new GeneratedSecretKey("secret", "hash"));
		when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CreateLinkResponse response = linkService.create(
				new CreateLinkRequest("https://example.com", null)
		);

		assertThat(response.code()).isEqualTo("bbbbbb");
	}

	@Test
	void rejectsPastExpirationBeforeSaving() {
		CreateLinkRequest request = new CreateLinkRequest(
				"https://example.com",
				Instant.now().minusSeconds(1)
		);

		assertThatThrownBy(() -> linkService.create(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("만료 시각은 현재보다 미래여야 합니다.");
		verify(linkRepository, never()).save(any(Link.class));
	}

	@Test
	void returnsManagedLinkWhenSecretMatches() {
		Instant expiresAt = Instant.now().plusSeconds(3600);
		Link link = managedLink(expiresAt);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("srrrg_sk_valid", "bcrypt-hash")).thenReturn(true);

		LinkManagementResponse response = linkService.getManagedLink("aB3x9Q", "srrrg_sk_valid");

		assertThat(response.code()).isEqualTo("aB3x9Q");
		assertThat(response.shortUrl()).isEqualTo("https://srrrg.link/aB3x9Q");
		assertThat(response.originalUrl()).isEqualTo("https://example.com/path");
		assertThat(response.expiresAt()).isEqualTo(expiresAt);
		assertThat(response.statistics().clickCount()).isEqualTo(12);
		assertThat(response.statistics().redirectCount()).isEqualTo(8);
	}

	@Test
	void hidesExistingLinkWhenSecretDoesNotMatch() {
		Link link = managedLink(null);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("wrong-secret", "bcrypt-hash")).thenReturn(false);

		assertThatThrownBy(() -> linkService.getManagedLink("aB3x9Q", "wrong-secret"))
				.isInstanceOf(LinkNotFoundException.class);
		verify(link, never()).updateOriginalUrl(any(String.class));
		verify(link, never()).updateExpiresAt(any(Instant.class));
		verify(link, never()).delete();
	}

	@Test
	void allowsManagingExpiredLink() {
		Link link = managedLink(Instant.now().minusSeconds(60));
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("srrrg_sk_valid", "bcrypt-hash")).thenReturn(true);

		LinkManagementResponse response = linkService.getManagedLink("aB3x9Q", "srrrg_sk_valid");

		assertThat(response.code()).isEqualTo("aB3x9Q");
	}

	@Test
	void updatesOnlyProvidedFields() {
		Instant expiresAt = Instant.now().plusSeconds(7200);
		Link link = managedLink(null);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("srrrg_sk_valid", "bcrypt-hash")).thenReturn(true);
		UpdateLinkRequest request = new UpdateLinkRequest();
		request.setOriginalUrl("https://new-example.com");
		request.setExpiresAt(expiresAt);

		linkService.updateManagedLink("aB3x9Q", "srrrg_sk_valid", request);

		verify(urlValidator).validate("https://new-example.com");
		verify(link).updateOriginalUrl("https://new-example.com");
		verify(link).updateExpiresAt(expiresAt);
	}

	@Test
	void clearsExpirationWhenExplicitNullIsProvided() {
		Link link = managedLink(Instant.now().plusSeconds(3600));
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("srrrg_sk_valid", "bcrypt-hash")).thenReturn(true);
		UpdateLinkRequest request = new UpdateLinkRequest();
		request.setExpiresAt(null);

		linkService.updateManagedLink("aB3x9Q", "srrrg_sk_valid", request);

		verify(link).updateExpiresAt(null);
		verify(link, never()).updateOriginalUrl(any(String.class));
	}

	@Test
	void rejectsEmptyUpdateRequest() {
		Link link = managedLink(null);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("srrrg_sk_valid", "bcrypt-hash")).thenReturn(true);

		assertThatThrownBy(() -> linkService.updateManagedLink(
				"aB3x9Q",
				"srrrg_sk_valid",
				new UpdateLinkRequest()
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("변경할 값을 하나 이상 입력해야 합니다.");
		verify(link, never()).updateOriginalUrl(any(String.class));
		verify(link, never()).updateExpiresAt(any(Instant.class));
	}

	@Test
	void softDeletesManagedLink() {
		Link link = managedLink(null);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("srrrg_sk_valid", "bcrypt-hash")).thenReturn(true);

		DeleteLinkResponse response = linkService.deleteManagedLink("aB3x9Q", "srrrg_sk_valid");

		assertThat(response.deleted()).isTrue();
		verify(link).delete();
	}

	@Test
	void rejectsAlreadyDeletedManagedLinkAfterAuthentication() {
		Link link = managedLink(null);
		when(link.isDeleted()).thenReturn(true);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(secretKeyManager.matches("srrrg_sk_valid", "bcrypt-hash")).thenReturn(true);

		assertThatThrownBy(() -> linkService.deleteManagedLink("aB3x9Q", "srrrg_sk_valid"))
				.isInstanceOf(LinkGoneException.class);
		verify(link, never()).delete();
	}

	@Test
	void resolvesTrustedLinkAndIncrementsClickAndRedirectCounts() {
		Link link = mock(Link.class);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(false);
		when(linkRepository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);
		when(linkRepository.incrementRedirectCountByCode("aB3x9Q")).thenReturn(1);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn("https://example.com/path");
		when(link.isTrusted()).thenReturn(true);

		RedirectLink redirectLink = linkService.resolveRedirect("aB3x9Q", requestInfo);

		assertThat(redirectLink.code()).isEqualTo("aB3x9Q");
		assertThat(redirectLink.originalUrl()).isEqualTo("https://example.com/path");
		assertThat(redirectLink.trusted()).isTrue();
		verify(clickEventRecorder).record(link, requestInfo);
		verify(redirectEventRecorder).record(link, requestInfo);
		verify(linkRepository).incrementClickCountByCode("aB3x9Q");
		verify(linkRepository).incrementRedirectCountByCode("aB3x9Q");
	}

	@Test
	void resolvesUntrustedLinkWithoutRedirectCount() {
		Link link = mock(Link.class);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(false);
		when(linkRepository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn("https://example.com/path");
		when(link.isTrusted()).thenReturn(false);

		RedirectLink redirectLink = linkService.resolveRedirect("aB3x9Q", requestInfo);

		assertThat(redirectLink.trusted()).isFalse();
		verify(clickEventRecorder).record(link, requestInfo);
		verify(redirectEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
		verify(linkRepository).incrementClickCountByCode("aB3x9Q");
		verify(linkRepository, never()).incrementRedirectCountByCode(any(String.class));
	}

	@Test
	void confirmedRedirectIncrementsOnlyRedirectCount() {
		Link link = mock(Link.class);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(false);
		when(linkRepository.incrementRedirectCountByCode("aB3x9Q")).thenReturn(1);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn("https://example.com/path");
		when(link.isTrusted()).thenReturn(false);

		RedirectLink redirectLink = linkService.confirmRedirect("aB3x9Q", requestInfo);

		assertThat(redirectLink.originalUrl()).isEqualTo("https://example.com/path");
		verify(clickEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
		verify(redirectEventRecorder).record(link, requestInfo);
		verify(linkRepository, never()).incrementClickCountByCode(any(String.class));
		verify(linkRepository).incrementRedirectCountByCode("aB3x9Q");
	}

	@Test
	void throwsNotFoundWhenCodeDoesNotExist() {
		when(linkRepository.findByCode("abcdef")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> linkService.resolveRedirect("abcdef", requestInfo))
				.isInstanceOf(LinkNotFoundException.class);
		verify(linkRepository, never()).incrementClickCountByCode(any(String.class));
		verify(linkRepository, never()).incrementRedirectCountByCode(any(String.class));
		verify(clickEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
		verify(redirectEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
	}

	@Test
	void throwsGoneAndDoesNotIncrementWhenLinkIsDeleted() {
		Link link = mock(Link.class);
		when(linkRepository.findByCode("deleted")).thenReturn(Optional.of(link));
		when(link.isDeleted()).thenReturn(true);

		assertThatThrownBy(() -> linkService.resolveRedirect("deleted", requestInfo))
				.isInstanceOf(LinkGoneException.class);
		verify(linkRepository, never()).incrementClickCountByCode(any(String.class));
		verify(linkRepository, never()).incrementRedirectCountByCode(any(String.class));
		verify(clickEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
		verify(redirectEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
	}

	@Test
	void throwsGoneAndDoesNotIncrementWhenLinkIsExpired() {
		Link link = mock(Link.class);
		when(linkRepository.findByCode("expired")).thenReturn(Optional.of(link));
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(true);

		assertThatThrownBy(() -> linkService.resolveRedirect("expired", requestInfo))
				.isInstanceOf(LinkGoneException.class);
		verify(linkRepository, never()).incrementClickCountByCode(any(String.class));
		verify(linkRepository, never()).incrementRedirectCountByCode(any(String.class));
		verify(clickEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
		verify(redirectEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
	}

	private Link managedLink(Instant expiresAt) {
		Link link = mock(Link.class);
		when(link.getCode()).thenReturn("aB3x9Q");
		when(link.getOriginalUrl()).thenReturn("https://example.com/path");
		when(link.getSecretKeyHash()).thenReturn("bcrypt-hash");
		when(link.getExpiresAt()).thenReturn(expiresAt);
		when(link.getClickCount()).thenReturn(12L);
		when(link.getRedirectCount()).thenReturn(8L);
		when(link.getCreatedAt()).thenReturn(Instant.parse("2026-07-10T10:00:00Z"));
		when(link.getUpdatedAt()).thenReturn(Instant.parse("2026-07-10T11:00:00Z"));
		return link;
	}
}

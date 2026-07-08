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
import link.srrrg.link.access.LinkAccessEventRecorder;
import link.srrrg.link.dto.CreateLinkRequest;
import link.srrrg.link.dto.CreateLinkResponse;

class LinkServiceTest {

	private final LinkRepository linkRepository = mock(LinkRepository.class);
	private final LinkCodeGenerator linkCodeGenerator = mock(LinkCodeGenerator.class);
	private final SecretKeyManager secretKeyManager = mock(SecretKeyManager.class);
	private final UrlValidator urlValidator = mock(UrlValidator.class);
	private final LinkAccessEventRecorder accessEventRecorder = mock(LinkAccessEventRecorder.class);
	private final ClientRequestInfo requestInfo = new ClientRequestInfo("203.0.113.10", null, "test-agent");

	private LinkService linkService;

	@BeforeEach
	void setUp() {
		linkService = new LinkService(
				linkRepository,
				linkCodeGenerator,
				secretKeyManager,
				urlValidator,
				accessEventRecorder,
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
	void resolvesActiveLinkAndIncrementsClickCount() {
		Link link = mock(Link.class);
		when(linkRepository.findByCode("aB3x9Q")).thenReturn(Optional.of(link));
		when(link.isDeleted()).thenReturn(false);
		when(link.isExpiredAt(any(Instant.class))).thenReturn(false);
		when(linkRepository.incrementClickCountByCode("aB3x9Q")).thenReturn(1);
		when(link.getOriginalUrl()).thenReturn("https://example.com/path");

		String originalUrl = linkService.resolveRedirect("aB3x9Q", requestInfo);

		assertThat(originalUrl).isEqualTo("https://example.com/path");
		verify(accessEventRecorder).record(link, requestInfo);
		verify(linkRepository).incrementClickCountByCode("aB3x9Q");
	}

	@Test
	void throwsNotFoundWhenCodeDoesNotExist() {
		when(linkRepository.findByCode("abcdef")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> linkService.resolveRedirect("abcdef", requestInfo))
				.isInstanceOf(LinkNotFoundException.class);
		verify(linkRepository, never()).incrementClickCountByCode(any(String.class));
		verify(accessEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
	}

	@Test
	void throwsGoneAndDoesNotIncrementWhenLinkIsDeleted() {
		Link link = mock(Link.class);
		when(linkRepository.findByCode("deleted")).thenReturn(Optional.of(link));
		when(link.isDeleted()).thenReturn(true);

		assertThatThrownBy(() -> linkService.resolveRedirect("deleted", requestInfo))
				.isInstanceOf(LinkGoneException.class);
		verify(linkRepository, never()).incrementClickCountByCode(any(String.class));
		verify(accessEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
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
		verify(accessEventRecorder, never()).record(any(Link.class), any(ClientRequestInfo.class));
	}
}

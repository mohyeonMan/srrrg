package link.srrrg.link;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.LinkClickEventRecorder;
import link.srrrg.link.access.LinkRedirectEventRecorder;
import link.srrrg.link.dto.CreateLinkRequest;
import link.srrrg.link.dto.CreateLinkResponse;
import link.srrrg.link.dto.RedirectLink;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LinkService {

	private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

	private final LinkRepository linkRepository;
	private final LinkCodeGenerator linkCodeGenerator;
	private final SecretKeyManager secretKeyManager;
	private final UrlValidator urlValidator;
	private final LinkClickEventRecorder clickEventRecorder;
	private final LinkRedirectEventRecorder redirectEventRecorder;
	private final String baseUrl;

	public LinkService(
			LinkRepository linkRepository,
			LinkCodeGenerator linkCodeGenerator,
			SecretKeyManager secretKeyManager,
			UrlValidator urlValidator,
			LinkClickEventRecorder clickEventRecorder,
			LinkRedirectEventRecorder redirectEventRecorder,
			@Value("${srrrg.base-url}") String baseUrl
	) {
		this.linkRepository = linkRepository;
		this.linkCodeGenerator = linkCodeGenerator;
		this.secretKeyManager = secretKeyManager;
		this.urlValidator = urlValidator;
		this.clickEventRecorder = clickEventRecorder;
		this.redirectEventRecorder = redirectEventRecorder;
		this.baseUrl = removeTrailingSlash(baseUrl);
	}

	public CreateLinkResponse create(CreateLinkRequest request) {
		urlValidator.validate(request.originalUrl());
		validateExpiration(request.expiresAt());

		String code = generateUniqueCode();
		GeneratedSecretKey secretKey = secretKeyManager.generate();
		Link link = Link.create(code, request.originalUrl(), secretKey.hash(), request.expiresAt());
		Link savedLink = linkRepository.save(link);

		return new CreateLinkResponse(
				savedLink.getCode(),
				baseUrl + "/" + savedLink.getCode(),
				secretKey.value(),
				savedLink.getExpiresAt()
		);
	}

	@Transactional
	public RedirectLink resolveRedirect(String code, ClientRequestInfo requestInfo) {
		Link link = findAvailableLink(code);

		clickEventRecorder.record(link, requestInfo);
		incrementClickCount(code);
		if (link.isTrusted()) {
			recordRedirect(link, code, requestInfo);
		}

		log.info("Redirect entry handled: code={}, trusted={}", code, link.isTrusted());
		return new RedirectLink(link.getCode(), link.getOriginalUrl(), link.isTrusted());
	}

	@Transactional
	public RedirectLink confirmRedirect(String code, ClientRequestInfo requestInfo) {
		Link link = findAvailableLink(code);
		recordRedirect(link, code, requestInfo);
		log.info("Confirm redirect handled: code={}", code);
		return new RedirectLink(link.getCode(), link.getOriginalUrl(), link.isTrusted());
	}

	private Link findAvailableLink(String code) {
		Link link = linkRepository.findByCode(code).orElseThrow(() -> {
			log.warn("Redirect link not found: code={}", code);
			return new LinkNotFoundException();
		});

		boolean deleted = link.isDeleted();
		boolean expired = link.isExpiredAt(Instant.now());
		if (deleted || expired) {
			log.info("Redirect link unavailable: code={}, deleted={}, expired={}", code, deleted, expired);
			throw new LinkGoneException();
		}
		return link;
	}

	private void incrementClickCount(String code) {
		int updatedRows = linkRepository.incrementClickCountByCode(code);
		if (updatedRows != 1) {
			log.warn("Redirect click count update failed: code={}, updatedRows={}", code, updatedRows);
			throw new LinkNotFoundException();
		}
	}

	private void recordRedirect(Link link, String code, ClientRequestInfo requestInfo) {
		redirectEventRecorder.record(link, requestInfo);
		int updatedRows = linkRepository.incrementRedirectCountByCode(code);
		if (updatedRows != 1) {
			log.warn("Redirect count update failed: code={}, updatedRows={}", code, updatedRows);
			throw new LinkNotFoundException();
		}
	}

	private String generateUniqueCode() {
		for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = linkCodeGenerator.generate();
			if (!linkRepository.existsByCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("단축 코드를 생성하지 못했습니다.");
	}

	private void validateExpiration(Instant expiresAt) {
		if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
			throw new IllegalArgumentException("만료 시각은 현재보다 미래여야 합니다.");
		}
	}

	private String removeTrailingSlash(String value) {
		if (value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}
}

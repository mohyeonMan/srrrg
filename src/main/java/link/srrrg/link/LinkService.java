package link.srrrg.link;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.dto.CreateLinkRequest;
import link.srrrg.link.dto.CreateLinkResponse;

@Service
public class LinkService {

	private static final Logger log = LoggerFactory.getLogger(LinkService.class);
	private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

	private final LinkRepository linkRepository;
	private final LinkCodeGenerator linkCodeGenerator;
	private final SecretKeyManager secretKeyManager;
	private final UrlValidator urlValidator;
	private final String baseUrl;

	public LinkService(
			LinkRepository linkRepository,
			LinkCodeGenerator linkCodeGenerator,
			SecretKeyManager secretKeyManager,
			UrlValidator urlValidator,
			@Value("${srrrg.base-url}") String baseUrl
	) {
		this.linkRepository = linkRepository;
		this.linkCodeGenerator = linkCodeGenerator;
		this.secretKeyManager = secretKeyManager;
		this.urlValidator = urlValidator;
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
	public String resolveRedirect(String code) {
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

		int updatedRows = linkRepository.incrementClickCountByCode(code);
		if (updatedRows != 1) {
			log.warn("Redirect click count update failed: code={}, updatedRows={}", code, updatedRows);
			throw new LinkNotFoundException();
		}
		log.info("Redirect succeeded: code={}", code);
		return link.getOriginalUrl();
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

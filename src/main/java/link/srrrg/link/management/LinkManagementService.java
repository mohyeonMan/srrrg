package link.srrrg.link.management;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Timer;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.domain.ProjectDomain;
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
import link.srrrg.link.management.dto.CreateLinkResponse;
import link.srrrg.link.management.dto.DeleteLinkResponse;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.LinkStatisticsSummary;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import link.srrrg.link.risk.RiskVerdict;
import link.srrrg.link.risk.UrlRiskVerificationService;
import link.srrrg.identity.User;
import link.srrrg.project.Project;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LinkManagementService {

	private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

	private final LinkRepository linkRepository;
	private final LinkCodeGenerator linkCodeGenerator;
	private final SecretKeyManager secretKeyManager;
	private final UrlValidator urlValidator;
	private final UrlRiskVerificationService riskVerificationService;
	private final SrrrgMetrics metrics;
	private final String baseUrl;

	public LinkManagementService(LinkRepository linkRepository, LinkCodeGenerator linkCodeGenerator,
			SecretKeyManager secretKeyManager, UrlValidator urlValidator,
			UrlRiskVerificationService riskVerificationService,
			SrrrgMetrics metrics,
			@Value("${srrrg.base-url}") String baseUrl) {
		this.linkRepository = linkRepository;
		this.linkCodeGenerator = linkCodeGenerator;
		this.secretKeyManager = secretKeyManager;
		this.urlValidator = urlValidator;
		this.riskVerificationService = riskVerificationService;
		this.metrics = metrics;
		this.baseUrl = removeTrailingSlash(baseUrl);
	}

	public CreateLinkResponse create(CreateLinkRequest request) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			log.debug("Link creation started: expiresAtPresent={}", request.expiresAt() != null);

			urlValidator.validate(request.originalUrl());
			validateExpiration(request.expiresAt());
			// 신규 링크는 위협 미탐지 결과가 있어야만 저장함.
			requireNoKnownThreat(request.originalUrl());

			GeneratedSecretKey secretKey = secretKeyManager.generate();
			Link savedLink = saveWithUniqueCode(request.originalUrl(), secretKey.hash(), request.expiresAt());
			log.info("Link created: code={}, expiresAtPresent={}", savedLink.getCode(), savedLink.getExpiresAt() != null);
			outcome = "created";
			return new CreateLinkResponse(savedLink.getCode(), baseUrl + "/" + savedLink.getCode(),
					secretKey.value(), savedLink.getExpiresAt());
		} catch (UnsafeUrlException exception) {
			outcome = "threat";
			throw exception;
		} catch (UrlRiskCheckFailedException exception) {
			outcome = "check_failed";
			throw exception;
		} catch (IllegalArgumentException exception) {
			outcome = "invalid";
			throw exception;
		} finally {
			metrics.recordLinkCreate(sample, outcome);
		}
	}

	@Transactional(readOnly = true)
	public LinkManagementResponse getManagedLink(String code, String secretKey) {
		return toManagementResponse(findManagedLink(code, secretKey));
	}

	public LinkManagementResponse updateManagedLink(String code, String secretKey, UpdateLinkRequest request) {
		// 외부 검사 시간 동안 DB 트랜잭션을 유지하지 않고 검사가 끝난 뒤 저장함.
		log.debug("Managed link update started: code={}", code);
		Link link = findManagedLink(code, secretKey);
		validateUpdateRequest(request);
		boolean urlChanged = request.isOriginalUrlPresent()
				&& !link.getOriginalUrl().equals(request.getOriginalUrl());
		if (request.isOriginalUrlPresent()) {
			urlValidator.validate(request.getOriginalUrl());
		}
		if (request.isExpiresAtPresent()) {
			validateExpiration(request.getExpiresAt());
		}
		if (urlChanged) {
			// 원본 URL이 실제로 바뀐 경우에만 새 URL을 검사함.
			requireNoKnownThreat(request.getOriginalUrl());
			link.updateOriginalUrl(request.getOriginalUrl());
		}
		if (request.isExpiresAtPresent()) {
			link.updateExpiresAt(request.getExpiresAt());
		}
		Link savedLink = linkRepository.save(link);
		log.info("Managed link updated: code={}, originalUrlChanged={}, expiresAtChanged={}",
				code, urlChanged, request.isExpiresAtPresent());
		return toManagementResponse(savedLink);
	}

	@Transactional
	public DeleteLinkResponse deleteManagedLink(String code, String secretKey) {
		findManagedLink(code, secretKey).delete();
		log.info("Managed link deleted: code={}", code);
		return new DeleteLinkResponse(true);
	}

	private void requireNoKnownThreat(String url) {
		RiskVerdict verdict = riskVerificationService.verify(url).verdict();
		if (verdict == RiskVerdict.THREAT) {
			log.warn("Link write rejected by URL risk check: verdict={}", verdict);
			throw new UnsafeUrlException();
		}
		if (verdict == RiskVerdict.UNKNOWN) {
			log.warn("Link write rejected by URL risk check: verdict={}", verdict);
			throw new UrlRiskCheckFailedException();
		}
		log.debug("Link write URL risk check completed: verdict={}", verdict);
	}

	private Link findManagedLink(String code, String secretKey) {
		Link link = linkRepository.findByCodeAndProjectIsNull(code).orElseThrow(() -> {
			log.info("Managed link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		if (!secretKeyManager.matches(secretKey, link.getSecretKeyHash())) {
			log.warn("Managed link authentication failed: code={}", code);
			throw new LinkNotFoundException();
		}
		if (link.isDeleted()) {
			log.info("Managed link unavailable: reason=DELETED, code={}", code);
			throw new LinkGoneException();
		}
		return link;
	}

	private LinkManagementResponse toManagementResponse(Link link) {
		return new LinkManagementResponse(link.getCode(), baseUrl + "/" + link.getCode(),
				link.getOriginalUrl(), link.getExpiresAt(),
				new LinkStatisticsSummary(link.getAccessCount(), link.getRedirectCount()),
				link.getCreatedAt(), link.getUpdatedAt());
	}

	public Link createForProject(CreateLinkRequest request, Project project, ProjectDomain domain, User createdBy) {
		return createForProject(request, project, domain, createdBy, null, null, null);
	}

	public Link createForProject(CreateLinkRequest request, Project project, ProjectDomain domain, User createdBy,
			Long apiKeyId, String idempotencyKey, String requestHash) {
		Link existing = findIdempotentLink(apiKeyId, idempotencyKey, requestHash);
		if (existing != null) return existing;
		urlValidator.validate(request.originalUrl());
		validateExpiration(request.expiresAt());
		requireNoKnownThreat(request.originalUrl());
		return saveProjectLinkWithUniqueCode(request.originalUrl(), request.expiresAt(), project, domain, createdBy,
				apiKeyId, idempotencyKey, requestHash);
	}

	private void validateUpdateRequest(UpdateLinkRequest request) {
		if (request == null || !request.hasChanges()) {
			throw new IllegalArgumentException("변경할 값을 하나 이상 입력해야 합니다.");
		}
	}

	private Link saveWithUniqueCode(String originalUrl, String secretKeyHash, Instant expiresAt) {
		for (int attempt = 1; attempt <= MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = linkCodeGenerator.generate();
			Link link = Link.create(code, originalUrl, secretKeyHash, expiresAt);
			try {
				return linkRepository.saveAndFlush(link);
			} catch (DataIntegrityViolationException exception) {
				metrics.recordLinkCodeGeneration("collision");
				log.debug("Generated link code collision: attempt={}, code={}", attempt, code);
			}
		}
		metrics.recordLinkCodeGeneration("exhausted");
		log.error("Link code generation exhausted: attempts={}", MAX_CODE_GENERATION_ATTEMPTS);
		throw new IllegalStateException("단축 코드를 생성하지 못했습니다.");
	}

	private Link saveProjectLinkWithUniqueCode(String originalUrl, Instant expiresAt, Project project, ProjectDomain domain, User createdBy,
			Long apiKeyId, String idempotencyKey, String requestHash) {
		for (int attempt = 1; attempt <= MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = linkCodeGenerator.generate();
			try {
				return linkRepository.saveAndFlush(Link.createForProject(code, originalUrl, expiresAt, project, domain, createdBy,
						apiKeyId, idempotencyKey, requestHash));
			} catch (DataIntegrityViolationException exception) {
				Link existing = findIdempotentLink(apiKeyId, idempotencyKey, requestHash);
				if (existing != null) return existing;
				metrics.recordLinkCodeGeneration("collision");
				log.debug("Generated project link code collision: attempt={}, code={}", attempt, code);
			}
		}
		metrics.recordLinkCodeGeneration("exhausted");
		throw new IllegalStateException("단축 코드를 생성하지 못했습니다.");
	}

	private Link findIdempotentLink(Long apiKeyId, String idempotencyKey, String requestHash) {
		if (apiKeyId == null || idempotencyKey == null) return null;
		return linkRepository.findByIdempotencyApiKeyIdAndIdempotencyKey(apiKeyId, idempotencyKey)
				.map(link -> {
					if (!requestHash.equals(link.getIdempotencyRequestHash())) throw new IdempotencyConflictException();
					return link;
				})
				.orElse(null);
	}

	public static class IdempotencyConflictException extends RuntimeException {
		public IdempotencyConflictException() { super("같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다."); }
	}

	private void validateExpiration(Instant expiresAt) {
		if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
			throw new IllegalArgumentException("만료 시각은 현재보다 미래여야 합니다.");
		}
	}

	private String removeTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}

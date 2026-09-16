package link.srrrg.link.creation.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import link.srrrg.campaign.model.Campaign;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.identity.account.model.User;
import link.srrrg.link.creation.model.LinkIdempotencyConflictException;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.model.ExternalIdConflictException;
import link.srrrg.link.model.Link;
import link.srrrg.link.model.LinkUtmValue;
import link.srrrg.link.repository.LinkRepository;
import link.srrrg.link.repository.LinkUtmValueRepository;
import link.srrrg.project.model.Project;
import link.srrrg.utmtemplate.model.UtmTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증된 프로젝트와 캠페인에서 링크를 생성한다.
 *
 * <p>코드 충돌과 API 멱등성은 여러 파드가 동시에 생성해도 DB 유일 제약을 최종 판정으로 삼는다.
 * 프로젝트·캠페인 경로는 인증된 주체가 만든다는 현재 정책에 따라 URL 위험 검사를 생략하지만,
 * URL 형식과 만료 시각은 링크 종류와 관계없이 검증한다.</p>
 */
@Service
@Slf4j
public class LinkCreationService {
	private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

	private final LinkRepository links;
	private final LinkUtmValueRepository linkUtmValues;
	private final LinkCodeGenerator codeGenerator;
	private final UrlValidator urlValidator;
	private final SrrrgMetrics metrics;

	public LinkCreationService(LinkRepository links, LinkUtmValueRepository linkUtmValues,
			LinkCodeGenerator codeGenerator, UrlValidator urlValidator, SrrrgMetrics metrics) {
		this.links = links;
		this.linkUtmValues = linkUtmValues;
		this.codeGenerator = codeGenerator;
		this.urlValidator = urlValidator;
		this.metrics = metrics;
	}

	public Link createForProject(CreateLinkRequest request, Project project, User createdBy) {
		return createForProject(request, project, createdBy, null, null, null);
	}

	/** API 키 호출에서는 기존 멱등 결과를 먼저 찾고, 같은 요청이면 새 링크를 만들지 않는다. */
	public Link createForProject(CreateLinkRequest request, Project project, User createdBy,
			Long apiKeyId, String idempotencyKey, String requestHash) {
		Link existing = findIdempotentLink(apiKeyId, idempotencyKey, requestHash);
		if (existing != null) return existing;
		urlValidator.validate(request.originalUrl());
		validateExpiration(request.expiresAt());
		return saveProjectLink(request.originalUrl(), request.expiresAt(), project, project.activeSubdomain(), createdBy,
				apiKeyId, idempotencyKey, requestHash, request.normalizedName());
	}

	public Link createForCampaign(String originalUrl, Instant expiresAt, Project project, User createdBy,
			Long apiKeyId, String idempotencyKey, String requestHash, Campaign campaign,
			UtmTemplate utmTemplate, String externalId, Map<String, String> resolvedUtmValues) {
		return createForCampaign(originalUrl, expiresAt, project, createdBy, apiKeyId, idempotencyKey,
				requestHash, campaign, utmTemplate, externalId, resolvedUtmValues, null);
	}

	/** 캠페인의 개별 목적지 또는 동적 기본 목적지를 검증하고 UTM 값은 인코딩해 별도로 저장한다. */
	public Link createForCampaign(String originalUrl, Instant expiresAt, Project project, User createdBy,
			Long apiKeyId, String idempotencyKey, String requestHash, Campaign campaign,
			UtmTemplate utmTemplate, String externalId, Map<String, String> resolvedUtmValues, String name) {
		Link existing = findIdempotentLink(apiKeyId, idempotencyKey, requestHash);
		if (existing != null) return existing;
		Map<String, String> utmByName = Map.copyOf(resolvedUtmValues);
		String currentDestination = originalUrl != null ? originalUrl : campaign.getDefaultOriginalUrl();
		if (currentDestination != null) urlValidator.validate(currentDestination);
		validateExpiration(expiresAt);
		Link link = saveCampaignLink(originalUrl, expiresAt, project, project.activeSubdomain(), createdBy,
				apiKeyId, idempotencyKey, requestHash, campaign, utmTemplate, externalId, name);
		// 요청에서 생략한 값은 저장하지 않아야 리다이렉트 시점의 캠페인 기본값을 상속한다.
		for (Map.Entry<String, String> entry : utmByName.entrySet()) {
			linkUtmValues.save(LinkUtmValue.create(link, entry.getKey(), entry.getValue()));
		}
		return link;
	}

	private Link saveProjectLink(String originalUrl, Instant expiresAt, Project project, String subdomain, User createdBy,
			Long apiKeyId, String idempotencyKey, String requestHash, String name) {
		for (int attempt = 1; attempt <= MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = codeGenerator.generate();
			try {
				return links.saveAndFlush(Link.createForProject(code, originalUrl, expiresAt, project, subdomain, createdBy,
						apiKeyId, idempotencyKey, requestHash, name));
			} catch (DataIntegrityViolationException exception) {
				Link existing = findIdempotentLink(apiKeyId, idempotencyKey, requestHash);
				if (existing != null) return existing;
				metrics.recordLinkCodeGeneration("collision");
				log.debug("Generated project link code collision: attempt={}, code={}", attempt, code);
			}
		}
		return exhausted();
	}

	private Link saveCampaignLink(String originalUrl, Instant expiresAt, Project project, String subdomain, User createdBy,
			Long apiKeyId, String idempotencyKey, String requestHash, Campaign campaign,
			UtmTemplate utmTemplate, String externalId, String name) {
		for (int attempt = 1; attempt <= MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = codeGenerator.generate();
			try {
				return links.saveAndFlush(Link.createForCampaign(code, originalUrl, expiresAt, project, subdomain, createdBy,
						apiKeyId, idempotencyKey, requestHash, campaign, utmTemplate, externalId, name));
			} catch (DataIntegrityViolationException exception) {
				if (isExternalIdConflict(exception)) throw new ExternalIdConflictException();
				Link existing = findIdempotentLink(apiKeyId, idempotencyKey, requestHash);
				if (existing != null) return existing;
				metrics.recordLinkCodeGeneration("collision");
				log.debug("Generated campaign link code collision: attempt={}, code={}", attempt, code);
			}
		}
		return exhausted();
	}

	private Link findIdempotentLink(Long apiKeyId, String idempotencyKey, String requestHash) {
		if (apiKeyId == null || idempotencyKey == null) return null;
		return links.findByIdempotencyApiKeyIdAndIdempotencyKey(apiKeyId, idempotencyKey)
				.map(link -> {
					if (!requestHash.equals(link.getIdempotencyRequestHash())) {
						throw new LinkIdempotencyConflictException();
					}
					return link;
				})
				.orElse(null);
	}

	private boolean isExternalIdConflict(DataIntegrityViolationException exception) {
		Throwable cause = exception.getMostSpecificCause();
		return cause != null && cause.getMessage() != null
				&& cause.getMessage().contains("uq_links_campaign_external_id");
	}

	private void validateExpiration(Instant expiresAt) {
		if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
			throw new IllegalArgumentException("만료 시각은 현재보다 미래여야 합니다.");
		}
	}

	private Link exhausted() {
		metrics.recordLinkCodeGeneration("exhausted");
		throw new IllegalStateException("단축 코드를 생성하지 못했습니다.");
	}
}

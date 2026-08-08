package link.srrrg.campaign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.campaign.dto.CreateCampaignLinkRequest;
import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.domain.ProjectDomain;
import link.srrrg.domain.ProjectDomainService;
import link.srrrg.identity.User;
import link.srrrg.link.Link;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectMemberRepository;

/**
 * campaign 링크 생성의 단일 진입점. web(JWT), 공개 API, JSON batch, CSV worker가 모두 이 서비스를 호출한다.
 * 요청값이 없는 UTM 필드는 캠페인 기본값으로 채우고, 최종 병합·검증·저장은 {@link LinkManagementService}에 위임한다.
 */
@Service
public class CampaignLinkCreationService {

	private static final java.util.regex.Pattern IDEMPOTENCY_KEY_PATTERN = java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{1,100}");

	private final CampaignService campaignService;
	private final UtmTemplateFieldRepository fields;
	private final LinkManagementService linkManagement;
	private final ProjectDomainService domains;
	private final ProjectMemberRepository members;
	private final RateLimitService rateLimitService;

	public CampaignLinkCreationService(CampaignService campaignService, UtmTemplateFieldRepository fields,
			LinkManagementService linkManagement,
			ProjectDomainService domains, ProjectMemberRepository members, RateLimitService rateLimitService) {
		this.campaignService = campaignService;
		this.fields = fields;
		this.linkManagement = linkManagement;
		this.domains = domains;
		this.members = members;
		this.rateLimitService = rateLimitService;
	}

	@Transactional
	public Link createForUser(Long userId, Long campaignId, CreateCampaignLinkRequest request) {
		Campaign campaign = campaignService.requireEditableCampaign(userId, campaignId);
		User createdBy = members.findByIdProjectIdAndIdUserId(campaign.getProject().getId(), userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."))
				.getUser();
		return create(campaign, campaign.getProject(), createdBy, null, null, null, request);
	}

	@Transactional
	public Link createForApiKey(Long apiKeyId, Long projectId, Long campaignId, String idempotencyKey, CreateCampaignLinkRequest request) {
		rateLimitService.checkApiKeyWrite(apiKeyId);
		Campaign campaign = campaignService.findForApiKey(projectId, campaignId);
		String normalizedKey = validIdempotencyKey(idempotencyKey);
		String requestHash = normalizedKey == null ? null : requestFingerprint(request);
		return create(campaign, campaign.getProject(), null, normalizedKey == null ? null : apiKeyId, normalizedKey, requestHash, request);
	}

	/**
	 * batch item은 batch 전체 단위로 idempotency를 다루므로 링크 단위 idempotency 없이 생성한다.
	 */
	@Transactional
	public Link createWithinBatch(Campaign campaign, CreateCampaignLinkRequest request) {
		return create(campaign, campaign.getProject(), null, null, null, null, request);
	}

	private Link create(Campaign campaign, Project project, User createdBy, Long apiKeyId, String idempotencyKey, String requestHash,
			CreateCampaignLinkRequest request) {
		UtmTemplate template = campaign.getUtmTemplate();
		Map<UtmTemplateField, String> resolved = resolveUtmValues(template, request.utmValuesOrEmpty());
		ProjectDomain domain = domains.get(project.getId());
		return linkManagement.createForCampaign(request.normalizedOriginalUrl(), request.expiresAt(), project, domain, createdBy,
				apiKeyId, idempotencyKey, requestHash, campaign, template, request.normalizedExternalId(), resolved);
	}

	public Map<UtmTemplateField, String> resolveUtmValues(UtmTemplate template, Map<String, String> requestValues) {
		if (template == null) {
			if (!requestValues.isEmpty()) {
				throw new IllegalArgumentException("캠페인에 선택된 UTM 템플릿이 없어 UTM 값을 받을 수 없습니다.");
			}
			return Map.of();
		}
		List<UtmTemplateField> activeFields = fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(template.getId());
		Map<String, UtmTemplateField> byName = new LinkedHashMap<>();
		for (UtmTemplateField field : activeFields) {
			byName.put(field.getName(), field);
		}
		for (String requestedName : requestValues.keySet()) {
			if (!byName.containsKey(requestedName)) {
				throw new IllegalArgumentException("활성 UTM 필드가 아닙니다: " + requestedName);
			}
		}
		Map<UtmTemplateField, String> resolved = new LinkedHashMap<>();
		for (UtmTemplateField field : activeFields) {
			String value = requestValues.get(field.getName());
			if (value != null && !value.isBlank()) {
				resolved.put(field, validUtmValue(value));
			}
		}
		return resolved;
	}

	private String validUtmValue(String value) {
		if (value.length() > 500) {
			throw new IllegalArgumentException("UTM 값은 500자 이하여야 합니다.");
		}
		return value;
	}

	private String validIdempotencyKey(String value) {
		if (value == null) return null;
		if (!IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
			throw new IllegalArgumentException("Idempotency-Key가 올바르지 않습니다.");
		}
		return value;
	}

	public String requestFingerprint(CreateCampaignLinkRequest request) {
		Map<String, String> sorted = new TreeMap<>(request.utmValuesOrEmpty());
		String payload = request.normalizedOriginalUrl() + "\n" + request.expiresAt() + "\n" + request.normalizedExternalId() + "\n" + sorted;
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

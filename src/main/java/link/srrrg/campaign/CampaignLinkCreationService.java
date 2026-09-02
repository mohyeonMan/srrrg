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
import link.srrrg.identity.User;
import link.srrrg.link.Link;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectMemberRepository;

/**
 * campaign 링크 생성의 단일 진입점. web(JWT), 공개 API, JSON batch, CSV worker가 모두 이 서비스를 호출한다.
 * 링크에 명시된 UTM만 저장하고, 최종 병합·검증·저장은 {@link LinkManagementService}에 위임한다.
 */
@Service
public class CampaignLinkCreationService {

	private static final java.util.regex.Pattern IDEMPOTENCY_KEY_PATTERN = java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{1,100}");

	private final CampaignService campaignService;
	private final UtmTemplateFieldRepository fields;
	private final LinkManagementService linkManagement;
	private final ProjectMemberRepository members;
	private final RateLimitService rateLimitService;

	public CampaignLinkCreationService(CampaignService campaignService, UtmTemplateFieldRepository fields,
			LinkManagementService linkManagement,
			ProjectMemberRepository members, RateLimitService rateLimitService) {
		this.campaignService = campaignService;
		this.fields = fields;
		this.linkManagement = linkManagement;
		this.members = members;
		this.rateLimitService = rateLimitService;
	}

	/**
	 * 웹에서 캠페인 링크를 만든다. 캠페인의 EDITOR 권한을 확인한 뒤 생성자를 기록한다.
	 * 링크 단위 멱등 키를 쓰지 않는 것은 화면에서 한 건씩 만드는 경로이기 때문이다.
	 */
	@Transactional
	public Link createForUser(Long userId, Long campaignId, CreateCampaignLinkRequest request) {
		Campaign campaign = campaignService.requireEditableCampaign(userId, campaignId);
		User createdBy = members.findActiveByProjectAndUser(campaign.getProject().getId(), userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."))
				.getUser();
		return create(campaign, campaign.getProject(), createdBy, null, null, null, request);
	}

	/**
	 * API key로 캠페인 링크를 만든다. 위 웹 경로와 짝을 이루며 인가는 캠페인이 그 프로젝트의 것인지로 판정한다.
	 *
	 * <p>멱등 키가 오면 요청 내용의 지문을 함께 저장한다. 네트워크 재시도로 같은 링크가 두 번 만들어지는 것을
	 * 막기 위한 것이며, 같은 키로 다른 내용을 보내면 충돌로 거부된다.</p>
	 */
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
		Map<String, String> resolved = resolveUtmValues(template, request.utmValuesOrEmpty());
		return linkManagement.createForCampaign(request.normalizedOriginalUrl(), request.expiresAt(), project, createdBy,
				apiKeyId, idempotencyKey, requestHash, campaign, template, request.normalizedExternalId(), resolved, request.normalizedName());
	}

	/**
	 * 요청에 담긴 UTM 값을 검증해 저장할 값만 추린다. CSV 임포트도 같은 규칙을 쓰기 위해 열어 둔 메서드다.
	 *
	 * <p>템플릿에 없는 필드 이름은 거부한다. 조용히 버리면 사용자는 값을 넣었다고 생각하는데
	 * 링크에는 반영되지 않는다. 반대로 값이 비어 있는 필드는 결과에서 빼는데, 저장하지 않아야
	 * 리다이렉트 시점에 캠페인 기본값을 상속하기 때문이다. 빈 문자열로 저장하면 기본값을 덮어써 버린다.</p>
	 *
	 * @param template 캠페인이 선택한 템플릿. {@code null}이면 UTM 값을 하나도 받을 수 없다
	 * @return 링크에 저장할 필드 이름과 값. 요청에 없거나 빈 값인 필드는 포함되지 않는다
	 * @throws IllegalArgumentException 활성 필드가 아닌 이름이거나 값이 길이 제한을 넘은 경우
	 */
	public Map<String, String> resolveUtmValues(UtmTemplate template, Map<String, String> requestValues) {
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
		Map<String, String> resolved = new LinkedHashMap<>();
		for (UtmTemplateField field : activeFields) {
			String value = requestValues.get(field.getName());
			if (value != null && !value.isBlank()) {
				resolved.put(field.getName(), validUtmValue(value));
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

	/**
	 * 멱등 판정에 쓸 요청 지문을 만든다. UTM 값을 정렬해서 넣는 것이 중요하다.
	 * Map의 순회 순서는 보장되지 않으므로, 정렬하지 않으면 같은 내용의 재시도가 다른 지문이 되어
	 * 멱등 충돌로 거부된다.
	 */
	public String requestFingerprint(CreateCampaignLinkRequest request) {
		Map<String, String> sorted = new TreeMap<>(request.utmValuesOrEmpty());
		String payload = request.normalizedOriginalUrl() + "\n" + request.expiresAt() + "\n" + request.normalizedExternalId()
				+ "\n" + request.normalizedName() + "\n" + sorted;
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

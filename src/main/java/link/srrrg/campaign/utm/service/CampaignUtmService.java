package link.srrrg.campaign.utm.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import link.srrrg.campaign.model.Campaign;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.campaign.utm.model.CampaignUtmDefault;
import link.srrrg.campaign.utm.repository.CampaignUtmDefaultRepository;
import link.srrrg.utmtemplate.model.UtmTemplate;
import link.srrrg.utmtemplate.model.UtmTemplateField;
import link.srrrg.utmtemplate.repository.UtmTemplateFieldRepository;
import link.srrrg.utmtemplate.repository.UtmTemplateRepository;
import link.srrrg.utmtemplate.service.UtmValueValidator;
import lombok.RequiredArgsConstructor;

/**
 * 캠페인이 사용할 UTM 템플릿과 필드별 기본값을 관리한다. 템플릿 정의 자체는 utmtemplate 기능이
 * 소유하며, 이 서비스는 캠페인과 정의의 연결 및 캠페인 기본값만 소유한다.
 */
@Service
@RequiredArgsConstructor
public class CampaignUtmService {
	private final CampaignService campaigns;
	private final UtmTemplateRepository templates;
	private final UtmTemplateFieldRepository fields;
	private final CampaignUtmDefaultRepository defaults;

	@Transactional
	public Campaign selectTemplate(Long userId, Long campaignId, Long templateId) {
		return applyTemplateSelection(campaigns.requireEditableCampaign(userId, campaignId), templateId);
	}

	@Transactional(readOnly = true)
	public List<CampaignUtmDefault> defaults(Long userId, Long campaignId) {
		return activeDefaults(campaigns.get(userId, campaignId));
	}

	@Transactional
	public void updateDefaults(Long userId, Long campaignId, Map<String, String> updates) {
		applyDefaultUpdates(campaigns.requireEditableCampaign(userId, campaignId), updates);
	}

	@Transactional
	public Campaign selectTemplateForApiKey(Long projectId, Long campaignId, Long templateId) {
		return applyTemplateSelection(campaigns.findForApiKey(projectId, campaignId), templateId);
	}

	@Transactional
	public void updateDefaultsForApiKey(Long projectId, Long campaignId, Map<String, String> updates) {
		applyDefaultUpdates(campaigns.findForApiKey(projectId, campaignId), updates);
	}

	@Transactional(readOnly = true)
	public List<CampaignUtmDefault> defaultsForApiKey(Long projectId, Long campaignId) {
		return activeDefaults(campaigns.findForApiKey(projectId, campaignId));
	}

	private Campaign applyTemplateSelection(Campaign campaign, Long templateId) {
		UtmTemplate template = null;
		if (templateId != null) {
			template = templates.findByIdAndProjectId(templateId, campaign.getProject().getId())
					.orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다."));
			if (template.isDeleted()) throw new IllegalArgumentException("템플릿을 찾을 수 없습니다.");
		}
		campaign.selectTemplate(template);
		return campaign;
	}

	/** 요청에 없는 필드는 유지하고, null 값은 삭제하며, 나머지 값은 필드 단위로 갱신한다. */
	private void applyDefaultUpdates(Campaign campaign, Map<String, String> updates) {
		UtmTemplate template = campaign.getUtmTemplate();
		if (template == null) throw new IllegalArgumentException("캠페인에 선택된 UTM 템플릿이 없습니다.");
		for (Map.Entry<String, String> entry : updates.entrySet()) {
			UtmTemplateField field = fields.findByUtmTemplateIdAndNameAndDeletedAtIsNull(template.getId(), entry.getKey())
					.orElseThrow(() -> new IllegalArgumentException("활성 필드가 아닙니다: " + entry.getKey()));
			var existing = defaults.findByCampaignIdAndFieldName(campaign.getId(), field.getName());
			if (entry.getValue() == null) {
				existing.ifPresent(defaults::delete);
			} else if (existing.isPresent()) {
				existing.get().updateValue(validDefaultValue(entry.getValue()));
			} else {
				try {
					defaults.saveAndFlush(CampaignUtmDefault.create(campaign, field.getName(), validDefaultValue(entry.getValue())));
				} catch (DataIntegrityViolationException exception) {
					throw new IllegalArgumentException("기본값을 저장하지 못했습니다.", exception);
				}
			}
		}
	}

	/** 삭제된 템플릿 필드의 보존 행은 숨기고 현재 활성 필드의 기본값만 반환한다. */
	private List<CampaignUtmDefault> activeDefaults(Campaign campaign) {
		if (campaign.getUtmTemplate() == null) return List.of();
		Set<String> active = fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(campaign.getUtmTemplate().getId())
				.stream().map(UtmTemplateField::getName).collect(Collectors.toSet());
		return defaults.findByCampaignIdOrderByFieldNameAsc(campaign.getId()).stream()
				.filter(value -> active.contains(value.getFieldName())).toList();
	}

	/**
	 * 기본값도 링크에 직접 넣은 값과 같은 길이 규칙을 쓴다. 리다이렉트 시점에는 둘이 하나로 합쳐져
	 * 같은 URL에 실리므로, 한쪽만 느슨하면 값별 상한이 전체 길이를 묶지 못한다.
	 */
	private String validDefaultValue(String value) {
		UtmValueValidator.validate(value);
		return value;
	}
}

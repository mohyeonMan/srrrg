package link.srrrg.campaign;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.campaign.importing.CampaignImportRepository;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;
import link.srrrg.link.LinkGoneException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.UrlValidator;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRepository;
import link.srrrg.project.ProjectRole;

@Service
public class CampaignService {

	private final CampaignRepository campaigns;
	private final UtmTemplateRepository templates;
	private final UtmTemplateFieldRepository fields;
	private final CampaignUtmDefaultRepository defaults;
	private final ProjectMemberRepository members;
	private final ProjectRepository projects;
	private final UserRepository users;
	private final LinkRepository links;
	private final CampaignImportRepository imports;
	private final UrlValidator urlValidator;

	public CampaignService(CampaignRepository campaigns, UtmTemplateRepository templates, UtmTemplateFieldRepository fields,
			CampaignUtmDefaultRepository defaults, ProjectMemberRepository members, ProjectRepository projects,
			UserRepository users, LinkRepository links, CampaignImportRepository imports, UrlValidator urlValidator) {
		this.campaigns = campaigns;
		this.templates = templates;
		this.fields = fields;
		this.defaults = defaults;
		this.members = members;
		this.projects = projects;
		this.users = users;
		this.links = links;
		this.imports = imports;
		this.urlValidator = urlValidator;
	}

	@Transactional
	public Campaign create(Long userId, Long projectId, String name, String description, String defaultOriginalUrl) {
		Project project = requireRole(userId, projectId, ProjectRole.EDITOR).getProject();
		return campaigns.save(Campaign.create(project, validName(name), validDescription(description),
				validDefaultOriginalUrl(defaultOriginalUrl), user(userId)));
	}

	@Transactional
	public Campaign createForApiKey(Long projectId, String name, String description, String defaultOriginalUrl) {
		Project project = projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
		return campaigns.save(Campaign.create(project, validName(name), validDescription(description),
				validDefaultOriginalUrl(defaultOriginalUrl), null));
	}

	@Transactional(readOnly = true)
	public List<Campaign> list(Long userId, Long projectId, Long cursor, int limit) {
		requireRole(userId, projectId, ProjectRole.VIEWER);
		return listPage(projectId, cursor, limit);
	}

	@Transactional(readOnly = true)
	public List<Campaign> listForApiKey(Long projectId, Long cursor, int limit) {
		return listPage(projectId, cursor, limit);
	}

	private List<Campaign> listPage(Long projectId, Long cursor, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		return cursor == null
				? campaigns.findByProjectIdAndArchivedAtIsNullOrderByIdDesc(projectId, page)
				: campaigns.findByProjectIdAndArchivedAtIsNullAndIdLessThanOrderByIdDesc(projectId, cursor, page);
	}

	@Transactional(readOnly = true)
	public Campaign get(Long userId, Long campaignId) {
		Campaign campaign = campaignOrNotFound(campaignId);
		requireRole(userId, campaign.getProject().getId(), ProjectRole.VIEWER);
		return unlessArchived(campaign);
	}

	@Transactional
	public Campaign rename(Long userId, Long campaignId, String name) {
		Campaign campaign = requireEditableCampaign(userId, campaignId);
		campaign.rename(validName(name));
		return campaign;
	}

	@Transactional
	public Campaign changeDescription(Long userId, Long campaignId, String description) {
		Campaign campaign = requireEditableCampaign(userId, campaignId);
		campaign.changeDescription(validDescription(description));
		return campaign;
	}

	@Transactional
	public Campaign changeDefaultOriginalUrl(Long userId, Long campaignId, String defaultOriginalUrl) {
		Campaign campaign = requireEditableCampaign(userId, campaignId);
		campaign.changeDefaultOriginalUrl(validDefaultOriginalUrl(defaultOriginalUrl));
		return campaign;
	}

	@Transactional
	public Campaign changeDefaultOriginalUrlForApiKey(Long projectId, Long campaignId, String defaultOriginalUrl) {
		Campaign campaign = findForApiKey(projectId, campaignId);
		campaign.changeDefaultOriginalUrl(validDefaultOriginalUrl(defaultOriginalUrl));
		return campaign;
	}

	@Transactional
	public Campaign renameForApiKey(Long projectId, Long campaignId, String name) {
		Campaign campaign = findForApiKey(projectId, campaignId);
		campaign.rename(validName(name));
		return campaign;
	}

	@Transactional
	public Campaign changeDescriptionForApiKey(Long projectId, Long campaignId, String description) {
		Campaign campaign = findForApiKey(projectId, campaignId);
		campaign.changeDescription(validDescription(description));
		return campaign;
	}

	@Transactional
	public void archive(Long userId, Long campaignId) {
		Campaign campaign = requireEditableCampaign(userId, campaignId);
		campaign.archive();
		links.softDeleteByCampaignId(campaignId);
		imports.cancelActiveByCampaignId(campaignId);
	}

	@Transactional
	public int deleteLinks(Long userId, Long campaignId, List<String> codes) {
		requireEditableCampaign(userId, campaignId);
		List<String> uniqueCodes = new java.util.ArrayList<>(new LinkedHashSet<>(codes));
		int deletedCount = links.softDeleteByCampaignIdAndCodeIn(campaignId, uniqueCodes);
		if (deletedCount != uniqueCodes.size()) {
			throw new IllegalArgumentException("선택한 링크 중 삭제할 수 없는 링크가 있습니다.");
		}
		return deletedCount;
	}

	@Transactional
	public Campaign selectTemplate(Long userId, Long campaignId, Long templateId) {
		Campaign campaign = requireEditableCampaign(userId, campaignId);
		return applyTemplateSelection(campaign, templateId);
	}

	@Transactional(readOnly = true)
	public List<CampaignUtmDefault> defaults(Long userId, Long campaignId) {
		Campaign campaign = get(userId, campaignId);
		return activeDefaults(campaign);
	}

	/**
	 * key가 없으면 해당 필드는 변경하지 않고, 값이 null이면 기본값을 삭제하고, 값이 있으면 upsert한다.
	 */
	@Transactional
	public void updateDefaults(Long userId, Long campaignId, Map<String, String> updates) {
		Campaign campaign = requireEditableCampaign(userId, campaignId);
		applyDefaultUpdates(campaign, updates);
	}

	@Transactional
	public Campaign selectTemplateForApiKey(Long projectId, Long campaignId, Long templateId) {
		Campaign campaign = findForApiKey(projectId, campaignId);
		return applyTemplateSelection(campaign, templateId);
	}

	@Transactional
	public void updateDefaultsForApiKey(Long projectId, Long campaignId, Map<String, String> updates) {
		Campaign campaign = findForApiKey(projectId, campaignId);
		applyDefaultUpdates(campaign, updates);
	}

	@Transactional(readOnly = true)
	public List<CampaignUtmDefault> defaultsForApiKey(Long projectId, Long campaignId) {
		Campaign campaign = findForApiKey(projectId, campaignId);
		return activeDefaults(campaign);
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

	private void applyDefaultUpdates(Campaign campaign, Map<String, String> updates) {
		UtmTemplate template = campaign.getUtmTemplate();
		if (template == null) {
			throw new IllegalArgumentException("캠페인에 선택된 UTM 템플릿이 없습니다.");
		}
		for (Map.Entry<String, String> entry : updates.entrySet()) {
			UtmTemplateField field = fields.findByUtmTemplateIdAndNameAndDeletedAtIsNull(template.getId(), entry.getKey())
					.orElseThrow(() -> new IllegalArgumentException("활성 필드가 아닙니다: " + entry.getKey()));
			String value = entry.getValue();
			var existing = defaults.findByCampaignIdAndFieldName(campaign.getId(), field.getName());
			if (value == null) {
				existing.ifPresent(defaults::delete);
				continue;
			}
			String trimmed = validDefaultValue(value);
			if (existing.isPresent()) {
				existing.get().updateValue(trimmed);
			} else {
				try {
					defaults.saveAndFlush(CampaignUtmDefault.create(campaign, field.getName(), trimmed));
				} catch (DataIntegrityViolationException exception) {
					throw new IllegalArgumentException("기본값을 저장하지 못했습니다.", exception);
				}
			}
		}
	}

	private List<CampaignUtmDefault> activeDefaults(Campaign campaign) {
		if (campaign.getUtmTemplate() == null) return List.of();
		java.util.Set<String> active = fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(campaign.getUtmTemplate().getId())
				.stream().map(UtmTemplateField::getName).collect(java.util.stream.Collectors.toSet());
		return defaults.findByCampaignIdOrderByFieldNameAsc(campaign.getId()).stream()
				.filter(value -> active.contains(value.getFieldName())).toList();
	}

	private Campaign campaignOrNotFound(Long campaignId) {
		return campaigns.findById(campaignId).orElseThrow(() -> new IllegalArgumentException("캠페인을 찾을 수 없습니다."));
	}

	private Campaign unlessArchived(Campaign campaign) {
		if (campaign.isArchived()) throw new LinkGoneException();
		return campaign;
	}

	@Transactional(readOnly = true)
	public Campaign requireEditableCampaign(Long userId, Long campaignId) {
		Campaign campaign = campaignOrNotFound(campaignId);
		requireRole(userId, campaign.getProject().getId(), ProjectRole.EDITOR);
		return unlessArchived(campaign);
	}

	@Transactional(readOnly = true)
	public Campaign findForApiKey(Long projectId, Long campaignId) {
		Campaign campaign = campaigns.findByIdAndProjectId(campaignId, projectId)
				.orElseThrow(() -> new IllegalArgumentException("캠페인을 찾을 수 없습니다."));
		return unlessArchived(campaign);
	}

	private ProjectMember requireRole(Long userId, Long projectId, ProjectRole minimum) {
		ProjectMember membership = members.findByIdProjectIdAndIdUserId(projectId, userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
		if (membership.getProject().getArchivedAt() != null) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		if (membership.getRole().ordinal() > minimum.ordinal()) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		return membership;
	}

	private User user(Long id) {
		return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	private String validName(String value) {
		if (value == null || value.trim().isEmpty() || value.trim().length() > 100) {
			throw new IllegalArgumentException("캠페인 이름은 1~100자로 입력하세요.");
		}
		return value.trim();
	}

	private String validDescription(String value) {
		if (value == null) return null;
		if (value.length() > 500) throw new IllegalArgumentException("캠페인 설명은 500자 이하로 입력하세요.");
		return value.isBlank() ? null : value;
	}

	private String validDefaultValue(String value) {
		if (value.length() > 500) throw new IllegalArgumentException("UTM 기본값은 500자 이하로 입력하세요.");
		return value;
	}

	private String validDefaultOriginalUrl(String value) {
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		urlValidator.validate(trimmed);
		// 인증된 프로젝트 멤버와 프로젝트 API key가 설정하므로 위험 검사는 의도적으로 생략한다.
		// 신뢰 정책이 바뀌면 LinkManagementService.requireNoKnownThreat와 같은 검사를 이 지점에 복구한다.
		return trimmed;
	}
}

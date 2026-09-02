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
import link.srrrg.link.LinkRepository;
import link.srrrg.link.UrlValidator;
import link.srrrg.project.Project;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRepository;
import link.srrrg.project.ProjectRole;

/**
 * 캠페인과 그 UTM 기본값을 다룬다. 캠페인은 링크 묶음이자 UTM 값의 상속 원천이라,
 * 여기서 바꾼 기본 목적지와 기본 UTM은 이미 만들어진 링크의 리다이렉트 결과까지 바꾼다.
 *
 * <p>거의 모든 공개 메서드가 웹용과 API key용으로 쌍을 이룬다. 웹은 {@link #requireRole}로 멤버십과 역할을
 * 확인하고, API key용({@code ...ForApiKey})은 키가 이미 프로젝트에 묶여 있어 대신
 * {@link #findForApiKey}가 캠페인이 그 프로젝트의 것인지 확인한다. 인가 방식만 다르고 이후 동작은
 * 같은 private 메서드를 공유하므로, 정책을 바꿀 때는 두 진입점을 함께 봐야 한다.</p>
 *
 * <p>캠페인의 기본 목적지는 URL 형식과 SSRF 검증만 하고 위험 검사는 생략한다.
 * 인증된 멤버나 프로젝트 API key가 설정한다는 신뢰 정책에 따른 것이며, 되돌릴 지점을
 * {@link #validDefaultOriginalUrl}에 적어 두었다.</p>
 */
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

	/**
	 * API key로 캠페인을 만든다. 위 웹 경로와 짝을 이루지만 멤버십 검사가 없다.
	 * 키가 이 프로젝트의 것인지 확인하는 책임은 호출자(컨트롤러)에 있으며, 생성자를 기록할 사용자가 없어 비워 둔다.
	 */
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

	/**
	 * 커서 기반 목록. 마지막으로 본 id보다 작은 것만 내림차순으로 가져오므로,
	 * 페이지를 넘기는 사이에 캠페인이 추가돼도 항목이 중복되거나 건너뛰지 않는다.
	 * 권한 확인은 호출자가 이미 끝냈다는 전제라 이 메서드는 직접 노출하지 않는다.
	 */
	private List<Campaign> listPage(Long projectId, Long cursor, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		return cursor == null
				? campaigns.findByProjectIdOrderByIdDesc(projectId, page)
				: campaigns.findByProjectIdAndIdLessThanOrderByIdDesc(projectId, cursor, page);
	}

	/**
	 * 캠페인을 조회한다. 캠페인을 먼저 찾아야 어느 프로젝트의 것인지 알 수 있어 권한 확인이 뒤에 온다.
	 * 그래서 없는 캠페인은 404, 남의 캠페인은 403으로 갈리며 존재 여부가 상태 코드로 드러난다.
	 * 캠페인 id는 순차 증가라 이 차이가 의미를 갖는다는 점을 알고 쓰는 구조다.
	 */
	@Transactional(readOnly = true)
	public Campaign get(Long userId, Long campaignId) {
		Campaign campaign = campaignOrNotFound(campaignId);
		requireRole(userId, campaign.getProject().getId(), ProjectRole.VIEWER);
		return campaign;
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

	/**
	 * 캠페인을 삭제하면서 딸린 것들을 함께 정리한다. 순서가 중요하다.
	 * 소속 링크를 먼저 soft delete하고, 진행 중인 CSV import를 취소한 뒤 캠페인을 지운다.
	 *
	 * <p>import를 취소하지 않으면 다른 파드의 worker가 이미 삭제된 캠페인에 링크를 계속 만든다.
	 * 링크를 남겨 두면 캠페인 없는 링크가 되어 목적지 상속이 끊긴다.</p>
	 */
	@Transactional
	public void delete(Long userId, Long campaignId) {
		Campaign campaign = requireEditableCampaign(userId, campaignId);
		links.softDeleteByCampaignId(campaignId);
		imports.cancelActiveByCampaignId(campaignId);
		campaigns.delete(campaign);
	}

	/**
	 * 선택한 링크들을 한 번에 삭제한다. 삭제된 개수가 요청 개수와 다르면 전체를 실패시킨다.
	 *
	 * <p>이 검사가 인가 역할을 겸한다. 조건에 캠페인 id가 들어 있어 다른 캠페인의 코드는 지워지지 않고,
	 * 개수가 어긋나므로 예외가 되어 트랜잭션이 롤백된다. 부분 성공을 허용하면 남의 코드를 섞어 보내
	 * 어떤 코드가 이 캠페인에 있는지 탐색할 수 있다.</p>
	 *
	 * @param codes 삭제할 단축 코드. 중복은 제거한 뒤 개수를 비교한다
	 * @throws IllegalArgumentException 하나라도 삭제되지 않은 경우. 이때 아무것도 삭제되지 않는다
	 */
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

	/**
	 * 캠페인이 쓸 UTM 템플릿을 바꾼다. {@code templateId}가 {@code null}이면 템플릿을 떼어 낸다.
	 *
	 * <p>템플릿을 같은 프로젝트 안에서만 찾는 것이 인가 지점이다. id만 받으면 다른 프로젝트의 템플릿을
	 * 가져다 쓸 수 있다. 삭제된 템플릿도 없는 것으로 처리한다.</p>
	 *
	 * <p>템플릿을 바꾸면 기존 기본값과 링크별 UTM은 남아 있지만, 새 템플릿에 없는 필드는
	 * 유효값 계산에서 빠져 리다이렉트 URL에 실리지 않는다.</p>
	 */
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

	/**
	 * UTM 기본값을 필드 단위로 반영한다. 요청에 없는 필드는 손대지 않고, 값이 {@code null}이면 삭제,
	 * 값이 있으면 새로 만들거나 갱신한다. 이 구분 덕분에 화면이 전체 값을 보내지 않고 바뀐 것만 보낼 수 있다.
	 *
	 * <p>여기서 바꾼 값은 이미 만들어진 링크에도 즉시 적용된다. 링크가 자기 값을 갖지 않은 필드는
	 * 리다이렉트 시점에 이 기본값을 따라가기 때문이다.</p>
	 *
	 * @throws IllegalArgumentException 템플릿이 없거나 활성 필드가 아닌 이름이 들어온 경우
	 */
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

	/**
	 * 현재 템플릿에 살아 있는 필드의 기본값만 돌려준다. 필드를 삭제해도 기본값 행은 남기 때문에,
	 * 걸러 내지 않으면 화면에 쓰이지 않는 값이 보인다. 행을 지우지 않는 것은 필드를 되살릴 때를 위한 것이다.
	 */
	private List<CampaignUtmDefault> activeDefaults(Campaign campaign) {
		if (campaign.getUtmTemplate() == null) return List.of();
		java.util.Set<String> active = fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(campaign.getUtmTemplate().getId())
				.stream().map(UtmTemplateField::getName).collect(java.util.stream.Collectors.toSet());
		return defaults.findByCampaignIdOrderByFieldNameAsc(campaign.getId()).stream()
				.filter(value -> active.contains(value.getFieldName())).toList();
	}

	private Campaign campaignOrNotFound(Long campaignId) {
		return campaigns.findById(campaignId).orElseThrow(CampaignNotFoundException::new);
	}

	/**
	 * 캠페인을 찾고 그 프로젝트에 대한 EDITOR 권한을 확인한다. 웹 경로의 쓰기 작업이 모두 이 메서드로 시작하며,
	 * 캠페인 링크 생성 서비스도 같은 관문을 쓴다.
	 *
	 * @throws CampaignNotFoundException 캠페인이 없는 경우
	 * @throws SecurityException 그 프로젝트의 EDITOR가 아닌 경우
	 */
	@Transactional(readOnly = true)
	public Campaign requireEditableCampaign(Long userId, Long campaignId) {
		Campaign campaign = campaignOrNotFound(campaignId);
		requireRole(userId, campaign.getProject().getId(), ProjectRole.EDITOR);
		return campaign;
	}

	/**
	 * API key 경로의 캠페인 관문. 캠페인 id만으로 찾지 않고 프로젝트 id를 조건에 함께 넣는 것이 핵심이다.
	 * 이것이 없으면 유효한 키로 다른 프로젝트의 캠페인을 조작할 수 있다.
	 * 다른 프로젝트의 캠페인은 없는 것과 같은 예외로 합쳐 존재 여부를 알려주지 않는다.
	 */
	@Transactional(readOnly = true)
	public Campaign findForApiKey(Long projectId, Long campaignId) {
		Campaign campaign = campaigns.findByIdAndProjectId(campaignId, projectId)
				.orElseThrow(CampaignNotFoundException::new);
		return campaign;
	}

	private ProjectMember requireRole(Long userId, Long projectId, ProjectRole minimum) {
		ProjectMember membership = members.findActiveByProjectAndUser(projectId, userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
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

	/**
	 * 캠페인 기본 목적지를 검증한다. 빈 값은 목적지 없음으로 보고 {@code null}로 저장하며,
	 * 그 경우 자체 목적지가 없는 링크는 리다이렉트에서 410으로 끝난다.
	 */
	private String validDefaultOriginalUrl(String value) {
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		urlValidator.validate(trimmed);
		// 인증된 프로젝트 멤버와 프로젝트 API key가 설정하므로 위험 검사는 의도적으로 생략한다.
		// 신뢰 정책이 바뀌면 LinkManagementService.requireNoKnownThreat와 같은 검사를 이 지점에 복구한다.
		return trimmed;
	}
}

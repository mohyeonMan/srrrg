package link.srrrg.campaign;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.project.Project;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.project.ProjectRepository;
import link.srrrg.project.ProjectRole;

/**
 * UTM 템플릿과 그 필드를 관리한다. 템플릿은 프로젝트 단위 자원이며, 여기서 정한 필드 목록이
 * 캠페인 링크에서 받을 수 있는 UTM의 전부다.
 *
 * <p>{@code CampaignService}와 마찬가지로 웹용과 API key용 메서드가 쌍을 이룬다.
 * 웹만 {@link #requireRole}로 역할을 확인하고, API key 경로는 컨트롤러가 키의 프로젝트를 확인했다는
 * 전제로 동작한다. 두 경로 모두 조회에 projectId를 함께 넘겨, 다른 프로젝트의 템플릿에 닿지 않게 한다.</p>
 *
 * <p>필드 삭제와 템플릿 삭제는 표시만 남기는 방식이다. 이미 그 템플릿으로 만들어진 링크가 있어
 * 물리 삭제를 할 수 없기 때문이다.</p>
 */
@Service
public class UtmTemplateService {

	public static final int MAX_ACTIVE_FIELDS = 10;
	// 필드 이름은 그대로 URL 쿼리 파라미터 이름이 되므로, 인코딩이 필요 없는 문자만 허용한다.
	// 활성 필드 수를 제한하는 것은 링크마다 붙는 파라미터가 늘어 URL 길이 제한에 닿는 것을 막기 위해서다.
	private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,49}$");
	private static final List<String> DEFAULT_FIELD_NAMES = List.of("utm_source", "utm_medium", "utm_campaign");

	private final UtmTemplateRepository templates;
	private final UtmTemplateFieldRepository fields;
	private final CampaignRepository campaigns;
	private final ProjectMemberRepository members;
	private final ProjectRepository projects;

	public UtmTemplateService(UtmTemplateRepository templates, UtmTemplateFieldRepository fields,
			CampaignRepository campaigns, ProjectMemberRepository members, ProjectRepository projects) {
		this.templates = templates;
		this.fields = fields;
		this.campaigns = campaigns;
		this.members = members;
		this.projects = projects;
	}

	@Transactional
	public UtmTemplate create(Long userId, Long projectId, String name) {
		Project project = requireRole(userId, projectId, ProjectRole.EDITOR).getProject();
		return doCreate(project, name);
	}

	@Transactional
	public UtmTemplate createForApiKey(Long projectId, String name) {
		return doCreate(project(projectId), name);
	}

	/**
	 * 프로젝트를 만들 때 함께 만드는 기본 템플릿. 템플릿이 하나도 없으면 캠페인에서 UTM을 쓸 수 없어,
	 * 빈 상태로 시작하지 않도록 표준 세 필드를 미리 넣는다. 프로젝트 생성 트랜잭션 안에서 호출된다.
	 */
	@Transactional
	public UtmTemplate createDefault(Project project) {
		UtmTemplate template = doCreate(project, "기본 템플릿");
		DEFAULT_FIELD_NAMES.forEach(name -> fields.save(UtmTemplateField.create(template, name)));
		return template;
	}

	@Transactional(readOnly = true)
	public List<UtmTemplate> list(Long userId, Long projectId) {
		requireRole(userId, projectId, ProjectRole.VIEWER);
		return templates.findByProjectIdAndDeletedAtIsNullOrderByIdDesc(projectId);
	}

	@Transactional(readOnly = true)
	public List<UtmTemplate> listForApiKey(Long projectId) {
		return templates.findByProjectIdAndDeletedAtIsNullOrderByIdDesc(projectId);
	}

	@Transactional(readOnly = true)
	public UtmTemplate get(Long userId, Long projectId, Long templateId) {
		requireRole(userId, projectId, ProjectRole.VIEWER);
		return template(templateId, projectId);
	}

	@Transactional(readOnly = true)
	public UtmTemplate getForApiKey(Long projectId, Long templateId) {
		return template(templateId, projectId);
	}

	@Transactional(readOnly = true)
	public List<UtmTemplateField> activeFields(Long userId, Long projectId, Long templateId) {
		requireRole(userId, projectId, ProjectRole.VIEWER);
		return doActiveFields(projectId, templateId);
	}

	@Transactional(readOnly = true)
	public List<UtmTemplateField> activeFieldsForApiKey(Long projectId, Long templateId) {
		return doActiveFields(projectId, templateId);
	}

	@Transactional
	public UtmTemplate rename(Long userId, Long projectId, Long templateId, String name) {
		requireRole(userId, projectId, ProjectRole.EDITOR);
		return doRename(projectId, templateId, name);
	}

	@Transactional
	public UtmTemplate renameForApiKey(Long projectId, Long templateId, String name) {
		return doRename(projectId, templateId, name);
	}

	@Transactional
	public void delete(Long userId, Long projectId, Long templateId) {
		requireRole(userId, projectId, ProjectRole.EDITOR);
		doDelete(projectId, templateId);
	}

	@Transactional
	public void deleteForApiKey(Long projectId, Long templateId) {
		doDelete(projectId, templateId);
	}

	@Transactional
	public UtmTemplateField addField(Long userId, Long projectId, Long templateId, String name) {
		requireRole(userId, projectId, ProjectRole.EDITOR);
		return doAddField(projectId, templateId, name);
	}

	@Transactional
	public UtmTemplateField addFieldForApiKey(Long projectId, Long templateId, String name) {
		return doAddField(projectId, templateId, name);
	}

	@Transactional
	public void deleteField(Long userId, Long projectId, Long templateId, Long fieldId) {
		requireRole(userId, projectId, ProjectRole.EDITOR);
		doDeleteField(projectId, templateId, fieldId);
	}

	@Transactional
	public void deleteFieldForApiKey(Long projectId, Long templateId, Long fieldId) {
		doDeleteField(projectId, templateId, fieldId);
	}

	private UtmTemplate doCreate(Project project, String name) {
		try {
			return templates.saveAndFlush(UtmTemplate.create(project, validName(name)));
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("이미 사용 중인 템플릿 이름입니다.", exception);
		}
	}

	private List<UtmTemplateField> doActiveFields(Long projectId, Long templateId) {
		template(templateId, projectId);
		return fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(templateId);
	}

	private UtmTemplate doRename(Long projectId, Long templateId, String name) {
		UtmTemplate template = template(templateId, projectId);
		try {
			template.rename(validName(name));
			templates.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("이미 사용 중인 템플릿 이름입니다.", exception);
		}
		return template;
	}

	/**
	 * 템플릿을 삭제 표시한다. 사용 중인 캠페인이 하나라도 있으면 거부한다.
	 * 그대로 지우면 그 캠페인의 링크가 UTM 필드 정의를 잃어 유효값 계산에서 모두 빠지기 때문이다.
	 */
	private void doDelete(Long projectId, Long templateId) {
		UtmTemplate template = template(templateId, projectId);
		if (campaigns.countByUtmTemplateId(templateId) > 0) {
			throw new IllegalArgumentException("이 템플릿을 사용 중인 캠페인이 있어 삭제할 수 없습니다. 먼저 캠페인의 템플릿을 변경하세요.");
		}
		template.delete();
	}

	/**
	 * 활성 필드를 추가한다. 템플릿을 행 잠금으로 읽는 것이 핵심이다.
	 *
	 * <p>필드 수 상한을 세고 나서 저장하는 두 단계라, 잠금이 없으면 동시에 들어온 두 요청이 모두
	 * 상한 미만을 보고 통과해 상한을 넘긴다. 이름 중복은 세는 것으로 막을 수 없어 유일 제약이 최종 판정이다.</p>
	 */
	private UtmTemplateField doAddField(Long projectId, Long templateId, String name) {
		UtmTemplate template = templates.lockByIdAndProjectId(templateId, projectId)
				.orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다."));
		if (template.isDeleted()) {
			throw new IllegalArgumentException("템플릿을 찾을 수 없습니다.");
		}
		if (fields.countByUtmTemplateIdAndDeletedAtIsNull(templateId) >= MAX_ACTIVE_FIELDS) {
			throw new IllegalArgumentException("템플릿에는 활성 필드를 최대 " + MAX_ACTIVE_FIELDS + "개까지 만들 수 있습니다.");
		}
		try {
			return fields.saveAndFlush(UtmTemplateField.create(template, validFieldName(name)));
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("이미 사용 중인 필드 이름입니다.", exception);
		}
	}

	private void doDeleteField(Long projectId, Long templateId, Long fieldId) {
		template(templateId, projectId);
		UtmTemplateField field = fields.findByIdAndUtmTemplateId(fieldId, templateId)
				.orElseThrow(() -> new IllegalArgumentException("필드를 찾을 수 없습니다."));
		field.delete();
	}

	/**
	 * 템플릿을 프로젝트 범위 안에서 찾는다. 이 클래스 인가의 마지막 관문이며,
	 * 다른 프로젝트의 템플릿과 삭제된 템플릿을 모두 같은 예외로 합쳐 존재 여부를 알려주지 않는다.
	 */
	UtmTemplate template(Long templateId, Long projectId) {
		UtmTemplate template = templates.findByIdAndProjectId(templateId, projectId)
				.orElseThrow(() -> new IllegalArgumentException("템플릿을 찾을 수 없습니다."));
		if (template.isDeleted()) {
			throw new IllegalArgumentException("템플릿을 찾을 수 없습니다.");
		}
		return template;
	}

	private Project project(Long projectId) {
		return projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
	}

	private ProjectMember requireRole(Long userId, Long projectId, ProjectRole minimum) {
		ProjectMember membership = members.findActiveByProjectAndUser(projectId, userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
		if (membership.getRole().ordinal() > minimum.ordinal()) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		return membership;
	}

	private String validName(String value) {
		if (value == null || value.trim().isEmpty() || value.trim().length() > 100) {
			throw new IllegalArgumentException("템플릿 이름은 1~100자로 입력하세요.");
		}
		return value.trim();
	}

	/**
	 * 필드 이름을 소문자로 맞추고 형식을 확인한다. 정규화하지 않으면 대소문자만 다른 같은 이름이
	 * 별개 필드로 만들어지고, 리다이렉트 URL에도 두 파라미터가 함께 실린다.
	 */
	private String validFieldName(String value) {
		if (value == null) throw new IllegalArgumentException("필드 이름을 입력하세요.");
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (!FIELD_NAME_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("필드 이름은 소문자로 시작하는 소문자·숫자·밑줄 2~50자여야 합니다.");
		}
		return normalized;
	}
}

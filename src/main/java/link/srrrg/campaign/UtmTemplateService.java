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

@Service
public class UtmTemplateService {

	public static final int MAX_ACTIVE_FIELDS = 10;
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

	private void doDelete(Long projectId, Long templateId) {
		UtmTemplate template = template(templateId, projectId);
		if (campaigns.countByUtmTemplateId(templateId) > 0) {
			throw new IllegalArgumentException("이 템플릿을 사용 중인 캠페인이 있어 삭제할 수 없습니다. 먼저 캠페인의 템플릿을 변경하세요.");
		}
		template.delete();
	}

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

	private String validFieldName(String value) {
		if (value == null) throw new IllegalArgumentException("필드 이름을 입력하세요.");
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (!FIELD_NAME_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("필드 이름은 소문자로 시작하는 소문자·숫자·밑줄 2~50자여야 합니다.");
		}
		return normalized;
	}
}

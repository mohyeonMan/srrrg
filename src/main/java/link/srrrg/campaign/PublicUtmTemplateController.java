package link.srrrg.campaign;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import link.srrrg.auth.ApiKeyRequestAuthorizer;
import link.srrrg.campaign.UtmTemplateController.CreateUtmTemplateFieldRequest;
import link.srrrg.campaign.UtmTemplateController.CreateUtmTemplateRequest;
import link.srrrg.campaign.UtmTemplateController.UtmTemplateFieldResponse;
import link.srrrg.campaign.UtmTemplateController.UtmTemplateResponse;
import link.srrrg.project.ApiKeyScope;

/**
 * API 키로 호출하는 UTM 템플릿 엔드포인트. 화면용 경로는 {@code UtmTemplateController}에 있다.
 * 인가 방식과 오류 형식이 다를 뿐 동작은 같은 서비스 메서드 쌍을 통해 일치시킨다.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "UTM templates")
public class PublicUtmTemplateController {
	private final UtmTemplateService templates;
	private final ApiKeyRequestAuthorizer apiKeyRequestAuthorizer;

	PublicUtmTemplateController(UtmTemplateService templates, ApiKeyRequestAuthorizer apiKeyRequestAuthorizer) {
		this.templates = templates;
		this.apiKeyRequestAuthorizer = apiKeyRequestAuthorizer;
	}

	@PostMapping("/projects/{projectId}/utm-templates")
	public ResponseEntity<UtmTemplateResponse> create(HttpServletRequest request, @PathVariable Long projectId,
			@RequestBody CreateUtmTemplateRequest body) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UtmTemplateResponse.from(templates.createForApiKey(projectId, body.name()), List.of()));
	}

	@GetMapping("/projects/{projectId}/utm-templates")
	public List<UtmTemplateResponse> list(HttpServletRequest request, @PathVariable Long projectId) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return templates.listForApiKey(projectId).stream()
				.map(template -> UtmTemplateResponse.from(template, templates.activeFieldsForApiKey(projectId, template.getId())))
				.toList();
	}

	@GetMapping("/projects/{projectId}/utm-templates/{templateId}")
	public UtmTemplateResponse get(HttpServletRequest request, @PathVariable Long projectId, @PathVariable Long templateId) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return UtmTemplateResponse.from(templates.getForApiKey(projectId, templateId), templates.activeFieldsForApiKey(projectId, templateId));
	}

	@PatchMapping("/projects/{projectId}/utm-templates/{templateId}")
	public UtmTemplateResponse rename(HttpServletRequest request, @PathVariable Long projectId, @PathVariable Long templateId,
			@RequestBody CreateUtmTemplateRequest body) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		UtmTemplate template = templates.renameForApiKey(projectId, templateId, body.name());
		return UtmTemplateResponse.from(template, templates.activeFieldsForApiKey(projectId, templateId));
	}

	@DeleteMapping("/projects/{projectId}/utm-templates/{templateId}")
	public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable Long projectId, @PathVariable Long templateId) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		templates.deleteForApiKey(projectId, templateId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/projects/{projectId}/utm-templates/{templateId}/fields")
	public ResponseEntity<UtmTemplateFieldResponse> addField(HttpServletRequest request, @PathVariable Long projectId,
			@PathVariable Long templateId, @RequestBody CreateUtmTemplateFieldRequest body) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UtmTemplateFieldResponse.from(templates.addFieldForApiKey(projectId, templateId, body.name())));
	}

	@DeleteMapping("/projects/{projectId}/utm-templates/{templateId}/fields/{fieldId}")
	public ResponseEntity<Void> deleteField(HttpServletRequest request, @PathVariable Long projectId,
			@PathVariable Long templateId, @PathVariable Long fieldId) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		templates.deleteFieldForApiKey(projectId, templateId, fieldId);
		return ResponseEntity.noContent().build();
	}

}

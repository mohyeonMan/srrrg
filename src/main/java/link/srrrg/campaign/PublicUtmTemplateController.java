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
import link.srrrg.campaign.UtmTemplateController.CreateUtmTemplateFieldRequest;
import link.srrrg.campaign.UtmTemplateController.CreateUtmTemplateRequest;
import link.srrrg.campaign.UtmTemplateController.UtmTemplateFieldResponse;
import link.srrrg.campaign.UtmTemplateController.UtmTemplateResponse;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.ApiKeyService.ApiKeyPrincipal;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "UTM templates")
public class PublicUtmTemplateController {
	private final UtmTemplateService templates;

	PublicUtmTemplateController(UtmTemplateService templates) {
		this.templates = templates;
	}

	@PostMapping("/projects/{projectId}/utm-templates")
	public ResponseEntity<UtmTemplateResponse> create(HttpServletRequest request, @PathVariable Long projectId,
			@RequestBody CreateUtmTemplateRequest body) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UtmTemplateResponse.from(templates.createForApiKey(projectId, body.name()), List.of()));
	}

	@GetMapping("/projects/{projectId}/utm-templates")
	public List<UtmTemplateResponse> list(HttpServletRequest request, @PathVariable Long projectId) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return templates.listForApiKey(projectId).stream()
				.map(template -> UtmTemplateResponse.from(template, templates.activeFieldsForApiKey(projectId, template.getId())))
				.toList();
	}

	@GetMapping("/projects/{projectId}/utm-templates/{templateId}")
	public UtmTemplateResponse get(HttpServletRequest request, @PathVariable Long projectId, @PathVariable Long templateId) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return UtmTemplateResponse.from(templates.getForApiKey(projectId, templateId), templates.activeFieldsForApiKey(projectId, templateId));
	}

	@PatchMapping("/projects/{projectId}/utm-templates/{templateId}")
	public UtmTemplateResponse rename(HttpServletRequest request, @PathVariable Long projectId, @PathVariable Long templateId,
			@RequestBody CreateUtmTemplateRequest body) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		UtmTemplate template = templates.renameForApiKey(projectId, templateId, body.name());
		return UtmTemplateResponse.from(template, templates.activeFieldsForApiKey(projectId, templateId));
	}

	@DeleteMapping("/projects/{projectId}/utm-templates/{templateId}")
	public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable Long projectId, @PathVariable Long templateId) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		templates.deleteForApiKey(projectId, templateId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/projects/{projectId}/utm-templates/{templateId}/fields")
	public ResponseEntity<UtmTemplateFieldResponse> addField(HttpServletRequest request, @PathVariable Long projectId,
			@PathVariable Long templateId, @RequestBody CreateUtmTemplateFieldRequest body) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UtmTemplateFieldResponse.from(templates.addFieldForApiKey(projectId, templateId, body.name())));
	}

	@DeleteMapping("/projects/{projectId}/utm-templates/{templateId}/fields/{fieldId}")
	public ResponseEntity<Void> deleteField(HttpServletRequest request, @PathVariable Long projectId,
			@PathVariable Long templateId, @PathVariable Long fieldId) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		templates.deleteFieldForApiKey(projectId, templateId, fieldId);
		return ResponseEntity.noContent().build();
	}

	private ApiKeyPrincipal principal(HttpServletRequest request, Long projectId, ApiKeyScope scope) {
		ApiKeyPrincipal principal = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (principal == null) throw new PublicApiException(401, "API_KEY_INVALID", "유효한 API key가 필요합니다.");
		if (!principal.projectId().equals(projectId)) throw new PublicApiException(403, "PROJECT_ACCESS_DENIED", "다른 프로젝트의 리소스에는 접근할 수 없습니다.");
		if (!principal.scopes().contains(scope)) throw new PublicApiException(403, "SCOPE_REQUIRED", scope.value() + " scope가 필요합니다.");
		return principal;
	}
}

package link.srrrg.campaign;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.SrrrgPrincipal;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class UtmTemplateController {
	private final UtmTemplateService templates;

	@PostMapping("/projects/{projectId}/utm-templates")
	public ResponseEntity<UtmTemplateResponse> create(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @Valid @RequestBody CreateUtmTemplateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UtmTemplateResponse.from(templates.create(principal.userId(), projectId, request.name()), List.of()));
	}

	@GetMapping("/projects/{projectId}/utm-templates")
	public List<UtmTemplateResponse> list(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId) {
		return templates.list(principal.userId(), projectId).stream()
				.map(template -> UtmTemplateResponse.from(template, templates.activeFields(principal.userId(), projectId, template.getId())))
				.toList();
	}

	@GetMapping("/projects/{projectId}/utm-templates/{templateId}")
	public UtmTemplateResponse get(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId, @PathVariable Long templateId) {
		return UtmTemplateResponse.from(templates.get(principal.userId(), projectId, templateId),
				templates.activeFields(principal.userId(), projectId, templateId));
	}

	@PatchMapping("/projects/{projectId}/utm-templates/{templateId}")
	public UtmTemplateResponse rename(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@PathVariable Long templateId, @Valid @RequestBody CreateUtmTemplateRequest request) {
		UtmTemplate template = templates.rename(principal.userId(), projectId, templateId, request.name());
		return UtmTemplateResponse.from(template, templates.activeFields(principal.userId(), projectId, templateId));
	}

	@DeleteMapping("/projects/{projectId}/utm-templates/{templateId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId, @PathVariable Long templateId) {
		templates.delete(principal.userId(), projectId, templateId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/projects/{projectId}/utm-templates/{templateId}/fields")
	public ResponseEntity<UtmTemplateFieldResponse> addField(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @PathVariable Long templateId, @Valid @RequestBody CreateUtmTemplateFieldRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UtmTemplateFieldResponse.from(templates.addField(principal.userId(), projectId, templateId, request.name())));
	}

	@DeleteMapping("/projects/{projectId}/utm-templates/{templateId}/fields/{fieldId}")
	public ResponseEntity<Void> deleteField(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@PathVariable Long templateId, @PathVariable Long fieldId) {
		templates.deleteField(principal.userId(), projectId, templateId, fieldId);
		return ResponseEntity.noContent().build();
	}

	public record CreateUtmTemplateRequest(@NotBlank @Size(max = 100) String name) { }
	public record CreateUtmTemplateFieldRequest(@NotBlank @Size(max = 50) String name) { }
	public record UtmTemplateFieldResponse(Long id, String name) {
		static UtmTemplateFieldResponse from(UtmTemplateField field) { return new UtmTemplateFieldResponse(field.getId(), field.getName()); }
	}
	public record UtmTemplateResponse(Long id, String name, Instant createdAt, Instant updatedAt, List<UtmTemplateFieldResponse> activeFields) {
		static UtmTemplateResponse from(UtmTemplate template, List<UtmTemplateField> fields) {
			return new UtmTemplateResponse(template.getId(), template.getName(), template.getCreatedAt(), template.getUpdatedAt(),
					fields.stream().map(UtmTemplateFieldResponse::from).toList());
		}
	}
}

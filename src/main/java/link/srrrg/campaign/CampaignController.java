package link.srrrg.campaign;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.SrrrgPrincipal;
import link.srrrg.campaign.dto.CreateCampaignLinkRequest;
import link.srrrg.campaign.dto.UpdateCampaignRequest;
import link.srrrg.campaign.importing.CampaignCsvService;
import link.srrrg.campaign.importing.CampaignImport;
import link.srrrg.campaign.importing.CampaignImportRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.project.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class CampaignController {

	private final CampaignService campaigns;
	private final CampaignLinkCreationService linkCreation;
	private final CampaignCsvService csv;
	private final CampaignImportRepository imports;
	private final LinkRepository links;
	private final ProjectMemberRepository members;

	@PostMapping("/projects/{projectId}/campaigns")
	public ResponseEntity<CampaignResponse> create(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@Valid @RequestBody CreateCampaignRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CampaignResponse.from(campaigns.create(principal.userId(), projectId, request.name(), request.description(),
						request.defaultOriginalUrl())));
	}

	@GetMapping("/projects/{projectId}/campaigns")
	public CampaignPageResponse list(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		int boundedLimit = boundedLimit(limit);
		List<Campaign> page = campaigns.list(principal.userId(), projectId, cursor, boundedLimit + 1);
		return CampaignPageResponse.of(page, boundedLimit);
	}

	@GetMapping("/campaigns/{campaignId}")
	public CampaignResponse get(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId) {
		return CampaignResponse.from(campaigns.get(principal.userId(), campaignId));
	}

	@PatchMapping("/campaigns/{campaignId}")
	public CampaignResponse update(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestBody UpdateCampaignRequest request) {
		if (!request.hasChanges()) throw new IllegalArgumentException("변경할 값을 하나 이상 입력해야 합니다.");
		Campaign campaign = null;
		if (request.isNamePresent()) campaign = campaigns.rename(principal.userId(), campaignId, request.getName());
		if (request.isDescriptionPresent()) campaign = campaigns.changeDescription(principal.userId(), campaignId, request.getDescription());
		if (request.isDefaultOriginalUrlPresent()) campaign = campaigns.changeDefaultOriginalUrl(principal.userId(), campaignId,
				request.getDefaultOriginalUrl());
		return CampaignResponse.from(campaign);
	}

	@DeleteMapping("/campaigns/{campaignId}")
	public ResponseEntity<Void> archive(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId) {
		campaigns.archive(principal.userId(), campaignId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/campaigns/{campaignId}/utm-template")
	public CampaignResponse selectTemplate(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestBody SelectTemplateRequest request) {
		return CampaignResponse.from(campaigns.selectTemplate(principal.userId(), campaignId, request.utmTemplateId()));
	}

	@GetMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> defaults(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId) {
		return toDefaultsMap(campaigns.defaults(principal.userId(), campaignId));
	}

	@PatchMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> updateDefaults(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestBody UpdateUtmDefaultsRequest request) {
		campaigns.updateDefaults(principal.userId(), campaignId, request.defaultsOrEmpty());
		return toDefaultsMap(campaigns.defaults(principal.userId(), campaignId));
	}

	@PostMapping("/campaigns/{campaignId}/links")
	public ResponseEntity<CampaignLinkResponse> createLink(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@Valid @RequestBody CreateCampaignLinkRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CampaignLinkResponse.from(linkCreation.createForUser(principal.userId(), campaignId, request)));
	}

	@GetMapping("/campaigns/{campaignId}/links")
	public CampaignLinkPageResponse links(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		campaigns.get(principal.userId(), campaignId);
		int boundedLimit = boundedLimit(limit);
		List<Link> page = cursor == null
				? links.findByCampaignIdAndDeletedFalseOrderByIdDesc(campaignId, org.springframework.data.domain.PageRequest.of(0, boundedLimit + 1))
				: links.findByCampaignIdAndDeletedFalseAndIdLessThanOrderByIdDesc(campaignId, cursor, org.springframework.data.domain.PageRequest.of(0, boundedLimit + 1));
		return CampaignLinkPageResponse.of(page, boundedLimit);
	}

	@GetMapping(value = "/campaigns/{campaignId}/links/template.csv", produces = "text/csv")
	public ResponseEntity<byte[]> templateCsv(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId) {
		Campaign campaign = campaigns.get(principal.userId(), campaignId);
		return csvAttachment(csv.templateCsv(campaign), "campaign-" + campaignId + "-template.csv");
	}

	@PostMapping("/campaigns/{campaignId}/imports/csv")
	public ResponseEntity<ImportResponse> uploadCsv(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestParam("file") MultipartFile file) throws java.io.IOException {
		Campaign campaign = campaigns.requireEditableCampaign(principal.userId(), campaignId);
		var uploader = members.findByIdProjectIdAndIdUserId(campaign.getProject().getId(), principal.userId())
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."))
				.getUser();
		CampaignImport created = csv.startImport(campaign, file.getBytes(), idempotencyKey, uploader, null);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportResponse.from(created));
	}

	@GetMapping("/campaigns/{campaignId}/imports/{importId}")
	public ImportResponse importStatus(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId, @PathVariable Long importId) {
		campaigns.get(principal.userId(), campaignId);
		return ImportResponse.from(importOrNotFound(campaignId, importId));
	}

	@GetMapping(value = "/campaigns/{campaignId}/imports/{importId}/errors.csv", produces = "text/csv")
	public ResponseEntity<byte[]> importErrorsCsv(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId, @PathVariable Long importId) {
		campaigns.get(principal.userId(), campaignId);
		CampaignImport campaignImport = importOrNotFound(campaignId, importId);
		return csvAttachment(csv.errorCsv(campaignImport), "import-" + importId + "-errors.csv");
	}

	@GetMapping(value = "/campaigns/{campaignId}/links.csv", produces = "text/csv")
	public ResponseEntity<byte[]> exportLinksCsv(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam(required = false) Instant createdFrom, @RequestParam(required = false) Instant createdTo,
			@RequestParam(required = false) String externalId) {
		Campaign campaign = campaigns.get(principal.userId(), campaignId);
		return csvAttachment(csv.exportLinksCsv(campaign, createdFrom, createdTo, externalId), "campaign-" + campaignId + "-links.csv");
	}

	private CampaignImport importOrNotFound(Long campaignId, Long importId) {
		return imports.findByIdAndCampaignId(importId, campaignId)
				.orElseThrow(() -> new IllegalArgumentException("import를 찾을 수 없습니다."));
	}

	private ResponseEntity<byte[]> csvAttachment(String content, String filename) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv"))
				.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
				.body(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private Map<String, String> toDefaultsMap(List<CampaignUtmDefault> defaults) {
		Map<String, String> map = new java.util.LinkedHashMap<>();
		for (CampaignUtmDefault campaignDefault : defaults) map.put(campaignDefault.getFieldName(), campaignDefault.getDefaultValue());
		return map;
	}

	private int boundedLimit(int limit) {
		if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit은 1~100 사이여야 합니다.");
		return limit;
	}

	public record CreateCampaignRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
			@Size(max = 2048) String defaultOriginalUrl) { }
	public record SelectTemplateRequest(Long utmTemplateId) { }
	public record UpdateUtmDefaultsRequest(Map<String, String> defaults) {
		public Map<String, String> defaultsOrEmpty() { return defaults == null ? Map.of() : defaults; }
	}
	public record CampaignResponse(Long id, String name, String description, String defaultOriginalUrl,
			Long utmTemplateId, String utmTemplateName,
			Instant createdAt, Instant updatedAt) {
		public static CampaignResponse from(Campaign campaign) {
			return new CampaignResponse(campaign.getId(), campaign.getName(), campaign.getDescription(), campaign.getDefaultOriginalUrl(),
					campaign.getUtmTemplate() == null ? null : campaign.getUtmTemplate().getId(),
					campaign.getUtmTemplate() == null ? null : campaign.getUtmTemplate().getName(),
					campaign.getCreatedAt(), campaign.getUpdatedAt());
		}
	}
	public record CampaignPageResponse(List<CampaignResponse> items, Long nextCursor) {
		static CampaignPageResponse of(List<Campaign> page, int limit) {
			List<Campaign> trimmed = page.size() > limit ? page.subList(0, limit) : page;
			Long nextCursor = page.size() > limit ? trimmed.get(trimmed.size() - 1).getId() : null;
			return new CampaignPageResponse(trimmed.stream().map(CampaignResponse::from).toList(), nextCursor);
		}
	}
	public record CampaignLinkResponse(String code, String originalUrl, String externalId, Instant createdAt) {
		static CampaignLinkResponse from(Link link) {
			return new CampaignLinkResponse(link.getCode(), link.getOriginalUrl(), link.getExternalId(), link.getCreatedAt());
		}
	}
	public record CampaignLinkPageResponse(List<CampaignLinkResponse> items, Long nextCursor) {
		static CampaignLinkPageResponse of(List<Link> page, int limit) {
			List<Link> trimmed = page.size() > limit ? page.subList(0, limit) : page;
			Long nextCursor = page.size() > limit ? trimmed.get(trimmed.size() - 1).getId() : null;
			return new CampaignLinkPageResponse(trimmed.stream().map(CampaignLinkResponse::from).toList(), nextCursor);
		}
	}
	public record ImportResponse(Long id, String status, int totalRows, int processedRows, int succeededRows, int failedRows,
			Instant createdAt, Instant completedAt) {
		static ImportResponse from(CampaignImport campaignImport) {
			return new ImportResponse(campaignImport.getId(), campaignImport.getStatus().name(), campaignImport.getTotalRows(),
					campaignImport.getProcessedRows(), campaignImport.getSucceededRows(), campaignImport.getFailedRows(),
					campaignImport.getCreatedAt(), campaignImport.getCompletedAt());
		}
	}
}

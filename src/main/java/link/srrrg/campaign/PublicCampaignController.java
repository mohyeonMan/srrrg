package link.srrrg.campaign;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import io.swagger.v3.oas.annotations.tags.Tag;
import link.srrrg.campaign.CampaignController.CampaignLinkPageResponse;
import link.srrrg.campaign.CampaignController.CampaignLinkResponse;
import link.srrrg.campaign.CampaignController.CampaignPageResponse;
import link.srrrg.campaign.CampaignController.CampaignResponse;
import link.srrrg.campaign.CampaignController.CreateCampaignRequest;
import link.srrrg.campaign.CampaignController.ImportResponse;
import link.srrrg.campaign.CampaignController.SelectTemplateRequest;
import link.srrrg.campaign.CampaignController.UpdateUtmDefaultsRequest;
import link.srrrg.campaign.dto.CreateCampaignLinkRequest;
import link.srrrg.campaign.dto.UpdateCampaignRequest;
import link.srrrg.campaign.importing.CampaignCsvService;
import link.srrrg.campaign.importing.CampaignImport;
import link.srrrg.campaign.importing.CampaignImportRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.ApiKeyService.ApiKeyPrincipal;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Campaigns")
public class PublicCampaignController {

	private final CampaignService campaigns;
	private final CampaignLinkCreationService linkCreation;
	private final CampaignLinkBatchService batches;
	private final CampaignCsvService csv;
	private final CampaignImportRepository imports;
	private final LinkRepository links;

	PublicCampaignController(CampaignService campaigns, CampaignLinkCreationService linkCreation, CampaignLinkBatchService batches,
			CampaignCsvService csv, CampaignImportRepository imports, LinkRepository links) {
		this.campaigns = campaigns;
		this.linkCreation = linkCreation;
		this.batches = batches;
		this.csv = csv;
		this.imports = imports;
		this.links = links;
	}

	@PostMapping("/projects/{projectId}/campaigns")
	public ResponseEntity<CampaignResponse> create(HttpServletRequest request, @PathVariable Long projectId, @RequestBody CreateCampaignRequest body) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CampaignResponse.from(campaigns.createForApiKey(projectId, body.name(), body.description(), body.defaultOriginalUrl())));
	}

	@PatchMapping("/campaigns/{campaignId}")
	public CampaignResponse update(HttpServletRequest request, @PathVariable Long campaignId, @RequestBody UpdateCampaignRequest body) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		if (!body.hasChanges()) throw new PublicApiException(400, "INVALID_REQUEST", "변경할 값을 하나 이상 입력해야 합니다.");
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		if (body.isNamePresent()) campaign = campaigns.renameForApiKey(projectId, campaignId, body.getName());
		if (body.isDescriptionPresent()) campaign = campaigns.changeDescriptionForApiKey(projectId, campaignId, body.getDescription());
		if (body.isDefaultOriginalUrlPresent()) {
			campaign = campaigns.changeDefaultOriginalUrlForApiKey(projectId, campaignId, body.getDefaultOriginalUrl());
		}
		return CampaignResponse.from(campaign);
	}

	@GetMapping("/projects/{projectId}/campaigns")
	public CampaignPageResponse list(HttpServletRequest request, @PathVariable Long projectId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		int boundedLimit = boundedLimit(limit);
		List<Campaign> page = campaigns.listForApiKey(projectId, cursor, boundedLimit + 1);
		return CampaignPageResponse.of(page, boundedLimit);
	}

	@GetMapping("/campaigns/{campaignId}")
	public CampaignResponse get(HttpServletRequest request, @PathVariable Long campaignId) {
		Campaign campaign = campaigns.findForApiKey(projectIdFrom(request), campaignId);
		principal(request, campaign.getProject().getId(), ApiKeyScope.CAMPAIGNS_READ);
		return CampaignResponse.from(campaign);
	}

	@PatchMapping("/campaigns/{campaignId}/utm-template")
	public CampaignResponse selectTemplate(HttpServletRequest request, @PathVariable Long campaignId, @RequestBody SelectTemplateRequest body) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return CampaignResponse.from(campaigns.selectTemplateForApiKey(projectId, campaignId, body.utmTemplateId()));
	}

	@GetMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> defaults(HttpServletRequest request, @PathVariable Long campaignId) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return toDefaultsMap(campaigns.defaultsForApiKey(projectId, campaignId));
	}

	@PatchMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> updateDefaults(HttpServletRequest request, @PathVariable Long campaignId, @RequestBody UpdateUtmDefaultsRequest body) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		campaigns.updateDefaultsForApiKey(projectId, campaignId, body.defaultsOrEmpty());
		return toDefaultsMap(campaigns.defaultsForApiKey(projectId, campaignId));
	}

	@PostMapping("/campaigns/{campaignId}/links")
	public ResponseEntity<CampaignLinkResponse> createLink(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, @RequestBody CreateCampaignLinkRequest body) {
		Long projectId = projectIdFrom(request);
		ApiKeyPrincipal principal = principal(request, projectId, ApiKeyScope.LINKS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CampaignLinkResponse.from(linkCreation.createForApiKey(principal.keyId(), projectId, campaignId, idempotencyKey, body)));
	}

	@PostMapping("/campaigns/{campaignId}/links/batch")
	public ResponseEntity<List<CampaignLinkResponse>> createBatch(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody List<CreateCampaignLinkRequest> items) {
		Long projectId = projectIdFrom(request);
		ApiKeyPrincipal principal = principal(request, projectId, ApiKeyScope.LINKS_WRITE);
		List<Link> created = batches.createBatch(principal.keyId(), projectId, campaignId, idempotencyKey, items);
		return ResponseEntity.status(HttpStatus.CREATED).body(created.stream().map(CampaignLinkResponse::from).toList());
	}

	@GetMapping("/campaigns/{campaignId}/links")
	public CampaignLinkPageResponse links(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.LINKS_READ);
		campaigns.findForApiKey(projectId, campaignId);
		int boundedLimit = boundedLimit(limit);
		List<Link> page = cursor == null
				? links.findByCampaignIdAndDeletedFalseOrderByIdDesc(campaignId, PageRequest.of(0, boundedLimit + 1))
				: links.findByCampaignIdAndDeletedFalseAndIdLessThanOrderByIdDesc(campaignId, cursor, PageRequest.of(0, boundedLimit + 1));
		return CampaignLinkPageResponse.of(page, boundedLimit);
	}

	@GetMapping(value = "/campaigns/{campaignId}/links/template.csv", produces = "text/csv")
	public ResponseEntity<byte[]> templateCsv(HttpServletRequest request, @PathVariable Long campaignId) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return csvAttachment(csv.templateCsv(campaign), "campaign-" + campaignId + "-template.csv");
	}

	@PostMapping("/campaigns/{campaignId}/imports/csv")
	public ResponseEntity<ImportResponse> uploadCsv(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestParam("file") MultipartFile file) throws java.io.IOException {
		Long projectId = projectIdFrom(request);
		ApiKeyPrincipal principal = principal(request, projectId, ApiKeyScope.LINKS_WRITE);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		CampaignImport created = csv.startImport(campaign, file.getBytes(), idempotencyKey, null, principal.keyId());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportResponse.from(created));
	}

	@GetMapping("/campaigns/{campaignId}/imports/{importId}")
	public ImportResponse importStatus(HttpServletRequest request, @PathVariable Long campaignId, @PathVariable Long importId) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		campaigns.findForApiKey(projectId, campaignId);
		return ImportResponse.from(importOrNotFound(campaignId, importId));
	}

	@GetMapping(value = "/campaigns/{campaignId}/imports/{importId}/errors.csv", produces = "text/csv")
	public ResponseEntity<byte[]> importErrorsCsv(HttpServletRequest request, @PathVariable Long campaignId, @PathVariable Long importId) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		campaigns.findForApiKey(projectId, campaignId);
		return csvAttachment(csv.errorCsv(importOrNotFound(campaignId, importId)), "import-" + importId + "-errors.csv");
	}

	@GetMapping(value = "/campaigns/{campaignId}/links.csv", produces = "text/csv")
	public ResponseEntity<byte[]> exportLinksCsv(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) Instant createdFrom, @RequestParam(required = false) Instant createdTo,
			@RequestParam(required = false) String externalId) {
		Long projectId = projectIdFrom(request);
		principal(request, projectId, ApiKeyScope.LINKS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return csvAttachment(csv.exportLinksCsv(campaign, createdFrom, createdTo, externalId), "campaign-" + campaignId + "-links.csv");
	}

	private CampaignImport importOrNotFound(Long campaignId, Long importId) {
		return imports.findByIdAndCampaignId(importId, campaignId)
				.orElseThrow(() -> new PublicApiException(404, "IMPORT_NOT_FOUND", "import를 찾을 수 없습니다."));
	}

	private Long projectIdFrom(HttpServletRequest request) {
		ApiKeyPrincipal principal = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (principal == null) throw new PublicApiException(401, "API_KEY_INVALID", "유효한 API key가 필요합니다.");
		return principal.projectId();
	}

	private ApiKeyPrincipal principal(HttpServletRequest request, Long projectId, ApiKeyScope scope) {
		ApiKeyPrincipal principal = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (principal == null) throw new PublicApiException(401, "API_KEY_INVALID", "유효한 API key가 필요합니다.");
		if (!principal.projectId().equals(projectId)) throw new PublicApiException(403, "PROJECT_ACCESS_DENIED", "다른 프로젝트의 리소스에는 접근할 수 없습니다.");
		if (!principal.scopes().contains(scope)) throw new PublicApiException(403, "SCOPE_REQUIRED", scope.value() + " scope가 필요합니다.");
		return principal;
	}

	private ResponseEntity<byte[]> csvAttachment(String content, String filename) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv"))
				.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
				.body(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private Map<String, String> toDefaultsMap(List<CampaignUtmDefault> defaults) {
		Map<String, String> map = new java.util.LinkedHashMap<>();
		for (CampaignUtmDefault campaignDefault : defaults) map.put(campaignDefault.getField().getName(), campaignDefault.getDefaultValue());
		return map;
	}

	private int boundedLimit(int limit) {
		if (limit < 1 || limit > 100) throw new PublicApiException(400, "INVALID_REQUEST", "limit은 1~100 사이여야 합니다.");
		return limit;
	}
}

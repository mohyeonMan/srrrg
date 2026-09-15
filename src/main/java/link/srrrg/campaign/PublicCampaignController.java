package link.srrrg.campaign;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

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
import link.srrrg.auth.ApiKeyRequestAuthorizer;
import link.srrrg.common.PublicApiException;
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
import link.srrrg.link.Link;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.ApiKeyPrincipal;

/**
 * API 키로 호출하는 캠페인 엔드포인트. 화면용 경로가 {@code CampaignController}에 따로 있고
 * 둘은 인증 방식과 오류 형식이 다르다.
 *
 * <p>인가는 {@link ApiKeyRequestAuthorizer}를 반드시 거친다. Spring Security의 인가 규칙은 이 경로를
 * 통과시키므로, 키의 프로젝트와 scope를 확인하지 않으면 유효한 키만으로 남의 프로젝트 캠페인을
 * 다룰 수 있다.</p>
 *
 * <p>오류는 {@code PublicApiExceptionHandler}가 RFC 7807 형식으로 바꾼다.
 * 이 컨트롤러에서 새 예외를 던질 때는 그 처리기에도 등록해야 웹용 형식으로 새어 나가지 않는다.</p>
 *
 * <p>링크 목록과 임포트 조회는 각각 {@link CampaignLinkQueryService}와 {@link CampaignCsvService}가
 * 캠페인 소유 범위를 확인한 뒤 수행한다. 컨트롤러는 API key 주체와 scope를 전달하고 공개 API 응답만 만든다.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Campaigns")
public class PublicCampaignController {

	private final CampaignService campaigns;
	private final CampaignLinkCreationService linkCreation;
	private final CampaignLinkBatchService batches;
	private final CampaignLinkQueryService linkQueries;
	private final CampaignCsvService csv;
	private final ApiKeyRequestAuthorizer apiKeyRequestAuthorizer;

	PublicCampaignController(CampaignService campaigns, CampaignLinkCreationService linkCreation, CampaignLinkBatchService batches,
			CampaignLinkQueryService linkQueries, CampaignCsvService csv,
			ApiKeyRequestAuthorizer apiKeyRequestAuthorizer) {
		this.campaigns = campaigns;
		this.linkCreation = linkCreation;
		this.batches = batches;
		this.linkQueries = linkQueries;
		this.csv = csv;
		this.apiKeyRequestAuthorizer = apiKeyRequestAuthorizer;
	}

	@PostMapping("/projects/{projectId}/campaigns")
	public ResponseEntity<CampaignResponse> create(HttpServletRequest request, @PathVariable Long projectId, @RequestBody CreateCampaignRequest body) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CampaignResponse.from(campaigns.createForApiKey(projectId, body.name(), body.description(), body.defaultOriginalUrl())));
	}

	@PatchMapping("/campaigns/{campaignId}")
	public CampaignResponse update(HttpServletRequest request, @PathVariable Long campaignId, @RequestBody UpdateCampaignRequest body) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
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
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		int boundedLimit = boundedLimit(limit);
		List<Campaign> page = campaigns.listForApiKey(projectId, cursor, boundedLimit + 1);
		return CampaignPageResponse.of(page, boundedLimit);
	}

	@GetMapping("/campaigns/{campaignId}")
	public CampaignResponse get(HttpServletRequest request, @PathVariable Long campaignId) {
		Campaign campaign = campaigns.findForApiKey(projectIdFrom(request), campaignId);
		apiKeyRequestAuthorizer.require(request, campaign.getProject().getId(), ApiKeyScope.CAMPAIGNS_READ);
		return CampaignResponse.from(campaign);
	}

	@PatchMapping("/campaigns/{campaignId}/utm-template")
	public CampaignResponse selectTemplate(HttpServletRequest request, @PathVariable Long campaignId, @RequestBody SelectTemplateRequest body) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return CampaignResponse.from(campaigns.selectTemplateForApiKey(projectId, campaignId, body.utmTemplateId()));
	}

	@GetMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> defaults(HttpServletRequest request, @PathVariable Long campaignId) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return toDefaultsMap(campaigns.defaultsForApiKey(projectId, campaignId));
	}

	@PatchMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> updateDefaults(HttpServletRequest request, @PathVariable Long campaignId, @RequestBody UpdateUtmDefaultsRequest body) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		campaigns.updateDefaultsForApiKey(projectId, campaignId, body.defaultsOrEmpty());
		return toDefaultsMap(campaigns.defaultsForApiKey(projectId, campaignId));
	}

	@PostMapping("/campaigns/{campaignId}/links")
	public ResponseEntity<CampaignLinkResponse> createLink(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, @RequestBody CreateCampaignLinkRequest body) {
		Long projectId = projectIdFrom(request);
		ApiKeyPrincipal principal = apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.LINKS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CampaignLinkResponse.from(linkCreation.createForApiKey(principal.keyId(), projectId, campaignId, idempotencyKey, body)));
	}

	@PostMapping("/campaigns/{campaignId}/links/batch")
	public ResponseEntity<List<CampaignLinkResponse>> createBatch(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody List<CreateCampaignLinkRequest> items) {
		Long projectId = projectIdFrom(request);
		ApiKeyPrincipal principal = apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.LINKS_WRITE);
		List<Link> created = batches.createBatch(principal.keyId(), projectId, campaignId, idempotencyKey, items);
		return ResponseEntity.status(HttpStatus.CREATED).body(created.stream().map(CampaignLinkResponse::from).toList());
	}

	@GetMapping("/campaigns/{campaignId}/links")
	public CampaignLinkPageResponse links(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.LINKS_READ);
		int boundedLimit = boundedLimit(limit);
		return CampaignLinkPageResponse.of(linkQueries.listForApiKey(projectId, campaignId, cursor, boundedLimit));
	}

	@GetMapping(value = "/campaigns/{campaignId}/links/template.csv", produces = "text/csv")
	public ResponseEntity<byte[]> templateCsv(HttpServletRequest request, @PathVariable Long campaignId) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return csvAttachment(csv.templateCsv(campaign), "campaign-" + campaignId + "-template.csv");
	}

	@PostMapping("/campaigns/{campaignId}/imports/csv")
	public ResponseEntity<ImportResponse> uploadCsv(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestParam("file") MultipartFile file) throws java.io.IOException {
		Long projectId = projectIdFrom(request);
		ApiKeyPrincipal principal = apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.LINKS_WRITE);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		CampaignImport created = csv.startImport(campaign, file.getBytes(), idempotencyKey, null, principal.keyId());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportResponse.from(created));
	}

	@GetMapping("/campaigns/{campaignId}/imports/{importId}")
	public ImportResponse importStatus(HttpServletRequest request, @PathVariable Long campaignId, @PathVariable Long importId) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return ImportResponse.from(csv.requireImport(campaign, importId));
	}

	@GetMapping(value = "/campaigns/{campaignId}/imports/{importId}/errors.csv", produces = "text/csv")
	public ResponseEntity<byte[]> importErrorsCsv(HttpServletRequest request, @PathVariable Long campaignId, @PathVariable Long importId) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return csvAttachment(csv.errorCsv(csv.requireImport(campaign, importId)), "import-" + importId + "-errors.csv");
	}

	@GetMapping(value = "/campaigns/{campaignId}/links.csv", produces = "text/csv")
	public ResponseEntity<byte[]> exportLinksCsv(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) Instant createdFrom, @RequestParam(required = false) Instant createdTo,
			@RequestParam(required = false) String externalId) {
		Long projectId = projectIdFrom(request);
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.LINKS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return csvAttachment(csv.exportLinksCsv(campaign, createdFrom, createdTo, externalId), "campaign-" + campaignId + "-links.csv");
	}

	private Long projectIdFrom(HttpServletRequest request) {
		return apiKeyRequestAuthorizer.requireAuthenticated(request).projectId();
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
		if (limit < 1 || limit > 100) throw new PublicApiException(400, "INVALID_REQUEST", "limit은 1~100 사이여야 합니다.");
		return limit;
	}
}

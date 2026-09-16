package link.srrrg.campaign.link.csv.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import link.srrrg.auth.request.service.ApiKeyRequestAuthorizer;
import link.srrrg.campaign.link.csv.controller.CampaignCsvController.ImportResponse;
import link.srrrg.campaign.link.csv.model.CampaignImport;
import link.srrrg.campaign.link.csv.service.CampaignCsvService;
import link.srrrg.campaign.model.Campaign;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.project.apikey.model.ApiKeyPrincipal;
import link.srrrg.project.apikey.model.ApiKeyScope;
import lombok.RequiredArgsConstructor;

/** API 키 표면에서 현재 CSV 임포트와 내보내기 계약을 담당한다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicCampaignCsvController {
	private final CampaignService campaigns;
	private final CampaignCsvService csv;
	private final ApiKeyRequestAuthorizer authorizer;

	@GetMapping(value = "/campaigns/{campaignId}/links/template.csv", produces = "text/csv")
	public ResponseEntity<byte[]> template(HttpServletRequest request, @PathVariable Long campaignId) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return attachment(csv.templateCsv(campaigns.findForApiKey(projectId, campaignId)),
				"campaign-" + campaignId + "-template.csv");
	}

	@PostMapping("/campaigns/{campaignId}/imports/csv")
	public ResponseEntity<ImportResponse> upload(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestParam("file") MultipartFile file) throws java.io.IOException {
		Long projectId = projectId(request);
		ApiKeyPrincipal principal = authorizer.require(request, projectId, ApiKeyScope.LINKS_WRITE);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		CampaignImport created = csv.startImport(campaign, file.getBytes(), idempotencyKey, null, principal.keyId());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportResponse.from(created));
	}

	@GetMapping("/campaigns/{campaignId}/imports/{importId}")
	public ImportResponse status(HttpServletRequest request, @PathVariable Long campaignId, @PathVariable Long importId) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return ImportResponse.from(csv.requireImport(campaigns.findForApiKey(projectId, campaignId), importId));
	}

	@GetMapping(value = "/campaigns/{campaignId}/imports/{importId}/errors.csv", produces = "text/csv")
	public ResponseEntity<byte[]> errors(HttpServletRequest request, @PathVariable Long campaignId, @PathVariable Long importId) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return attachment(csv.errorCsv(csv.requireImport(campaign, importId)), "import-" + importId + "-errors.csv");
	}

	@GetMapping(value = "/campaigns/{campaignId}/links.csv", produces = "text/csv")
	public ResponseEntity<byte[]> export(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) Instant createdFrom, @RequestParam(required = false) Instant createdTo,
			@RequestParam(required = false) String externalId) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.LINKS_READ);
		Campaign campaign = campaigns.findForApiKey(projectId, campaignId);
		return attachment(csv.exportLinksCsv(campaign, createdFrom, createdTo, externalId),
				"campaign-" + campaignId + "-links.csv");
	}

	private Long projectId(HttpServletRequest request) { return authorizer.requireAuthenticated(request).projectId(); }
	private ResponseEntity<byte[]> attachment(String content, String filename) {
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
				.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
				.body(content.getBytes(StandardCharsets.UTF_8));
	}
}

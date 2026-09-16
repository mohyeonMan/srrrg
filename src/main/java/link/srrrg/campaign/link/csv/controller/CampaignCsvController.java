package link.srrrg.campaign.link.csv.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.campaign.link.csv.model.CampaignImport;
import link.srrrg.campaign.link.csv.service.CampaignCsvService;
import link.srrrg.campaign.model.Campaign;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.project.membership.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;

/** 현재 CSV 임포트·내보내기 HTTP 계약을 담당하며 worker와 lease 동작은 그대로 유지한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class CampaignCsvController {
	private final CampaignService campaigns;
	private final CampaignCsvService csv;
	private final ProjectAccessService projectAccess;

	@GetMapping(value = "/campaigns/{campaignId}/links/template.csv", produces = "text/csv")
	public ResponseEntity<byte[]> template(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId) {
		Campaign campaign = campaigns.get(principal.userId(), campaignId);
		return attachment(csv.templateCsv(campaign), "campaign-" + campaignId + "-template.csv");
	}

	@PostMapping("/campaigns/{campaignId}/imports/csv")
	public ResponseEntity<ImportResponse> upload(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestParam("file") MultipartFile file) throws java.io.IOException {
		Campaign campaign = campaigns.requireEditableCampaign(principal.userId(), campaignId);
		var uploader = projectAccess.requireRole(principal.userId(), campaign.getProject().getId(), ProjectRole.EDITOR).getUser();
		CampaignImport created = csv.startImport(campaign, file.getBytes(), idempotencyKey, uploader, null);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ImportResponse.from(created));
	}

	@GetMapping("/campaigns/{campaignId}/imports/{importId}")
	public ImportResponse status(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@PathVariable Long importId) {
		return ImportResponse.from(csv.requireImport(campaigns.get(principal.userId(), campaignId), importId));
	}

	@GetMapping(value = "/campaigns/{campaignId}/imports/{importId}/errors.csv", produces = "text/csv")
	public ResponseEntity<byte[]> errors(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@PathVariable Long importId) {
		CampaignImport value = csv.requireImport(campaigns.get(principal.userId(), campaignId), importId);
		return attachment(csv.errorCsv(value), "import-" + importId + "-errors.csv");
	}

	@GetMapping(value = "/campaigns/{campaignId}/links.csv", produces = "text/csv")
	public ResponseEntity<byte[]> export(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam(required = false) Instant createdFrom, @RequestParam(required = false) Instant createdTo,
			@RequestParam(required = false) String externalId) {
		Campaign campaign = campaigns.get(principal.userId(), campaignId);
		return attachment(csv.exportLinksCsv(campaign, createdFrom, createdTo, externalId),
				"campaign-" + campaignId + "-links.csv");
	}

	private ResponseEntity<byte[]> attachment(String content, String filename) {
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
				.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
				.body(content.getBytes(StandardCharsets.UTF_8));
	}

	public record ImportResponse(Long id, String status, int totalRows, int processedRows, int succeededRows, int failedRows,
			Instant createdAt, Instant completedAt) {
		public static ImportResponse from(CampaignImport value) {
			return new ImportResponse(value.getId(), value.getStatus().name(), value.getTotalRows(), value.getProcessedRows(),
					value.getSucceededRows(), value.getFailedRows(), value.getCreatedAt(), value.getCompletedAt());
		}
	}
}

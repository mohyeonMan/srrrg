package link.srrrg.campaign.link.csv.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.campaign.link.csv.service.CampaignCsvService;
import link.srrrg.campaign.model.Campaign;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.project.membership.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;

/**
 * 쿠키 JWT 표면의 CSV 대량 생성·내보내기 HTTP 계약을 담당한다.
 * 업로드는 요청 안에서 끝나므로 작업 접수 응답도, 진행률 조회 endpoint도 없다.
 */
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
	public CsvUploadResponse upload(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam("file") MultipartFile file) throws java.io.IOException {
		Campaign campaign = campaigns.requireEditableCampaign(principal.userId(), campaignId);
		var uploader = projectAccess.requireRole(principal.userId(), campaign.getProject().getId(), ProjectRole.EDITOR).getUser();
		int createdRows = csv.createLinks(campaign, file.getBytes(), uploader, null);
		return new CsvUploadResponse(createdRows, createdRows);
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

	public record CsvUploadResponse(int totalRows, int createdRows) { }
}

package link.srrrg.campaign.link.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import link.srrrg.auth.request.service.ApiKeyRequestAuthorizer;
import link.srrrg.campaign.link.batch.service.CampaignLinkBatchService;
import link.srrrg.campaign.link.controller.CampaignLinkController.CampaignLinkPageResponse;
import link.srrrg.campaign.link.controller.CampaignLinkController.CampaignLinkResponse;
import link.srrrg.campaign.link.dto.CreateCampaignLinkRequest;
import link.srrrg.campaign.link.service.CampaignLinkCreationService;
import link.srrrg.campaign.link.service.CampaignLinkQueryService;
import link.srrrg.link.model.Link;
import link.srrrg.project.apikey.model.ApiKeyPrincipal;
import link.srrrg.project.apikey.model.ApiKeyScope;
import link.srrrg.web.error.model.PublicApiException;
import lombok.RequiredArgsConstructor;

/** API 키 표면에서 캠페인 링크의 단건·일괄 발행과 조회 계약을 담당한다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicCampaignLinkController {
	private final CampaignLinkCreationService linkCreation;
	private final CampaignLinkBatchService batches;
	private final CampaignLinkQueryService linkQueries;
	private final ApiKeyRequestAuthorizer authorizer;

	@PostMapping("/campaigns/{campaignId}/links")
	public ResponseEntity<CampaignLinkResponse> create(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody CreateCampaignLinkRequest body) {
		Long projectId = projectId(request);
		ApiKeyPrincipal principal = authorizer.require(request, projectId, ApiKeyScope.LINKS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED).body(CampaignLinkResponse.from(
				linkCreation.createForApiKey(principal.keyId(), projectId, campaignId, idempotencyKey, body)));
	}

	@PostMapping("/campaigns/{campaignId}/links/batch")
	public ResponseEntity<List<CampaignLinkResponse>> createBatch(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody List<CreateCampaignLinkRequest> items) {
		Long projectId = projectId(request);
		ApiKeyPrincipal principal = authorizer.require(request, projectId, ApiKeyScope.LINKS_WRITE);
		List<Link> created = batches.createBatch(principal.keyId(), projectId, campaignId, idempotencyKey, items);
		return ResponseEntity.status(HttpStatus.CREATED).body(created.stream().map(CampaignLinkResponse::from).toList());
	}

	@GetMapping("/campaigns/{campaignId}/links")
	public CampaignLinkPageResponse list(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.LINKS_READ);
		if (limit < 1 || limit > 100) throw new PublicApiException(400, "INVALID_REQUEST", "limit은 1~100 사이여야 합니다.");
		return CampaignLinkPageResponse.of(linkQueries.listForApiKey(projectId, campaignId, cursor, limit));
	}

	private Long projectId(HttpServletRequest request) { return authorizer.requireAuthenticated(request).projectId(); }
}

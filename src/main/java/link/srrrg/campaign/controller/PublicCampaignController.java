package link.srrrg.campaign.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import link.srrrg.auth.request.service.ApiKeyRequestAuthorizer;
import link.srrrg.campaign.controller.CampaignController.CampaignPageResponse;
import link.srrrg.campaign.controller.CampaignController.CampaignResponse;
import link.srrrg.campaign.controller.CampaignController.CreateCampaignRequest;
import link.srrrg.campaign.dto.UpdateCampaignRequest;
import link.srrrg.campaign.model.Campaign;
import link.srrrg.campaign.service.CampaignService;
import link.srrrg.project.apikey.model.ApiKeyScope;
import link.srrrg.web.error.model.PublicApiException;
import lombok.RequiredArgsConstructor;

/** API 키 표면에서 캠페인 자체의 생명주기 계약을 담당한다. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Campaigns")
@RequiredArgsConstructor
public class PublicCampaignController {
	private final CampaignService campaigns;
	private final ApiKeyRequestAuthorizer apiKeyRequestAuthorizer;

	@PostMapping("/projects/{projectId}/campaigns")
	public ResponseEntity<CampaignResponse> create(HttpServletRequest request, @PathVariable Long projectId,
			@RequestBody CreateCampaignRequest body) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return ResponseEntity.status(HttpStatus.CREATED).body(CampaignResponse.from(
				campaigns.createForApiKey(projectId, body.name(), body.description(), body.defaultOriginalUrl())));
	}

	@PatchMapping("/campaigns/{campaignId}")
	public CampaignResponse update(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestBody UpdateCampaignRequest body) {
		Long projectId = projectId(request);
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
		Campaign campaign = campaigns.findForApiKey(projectId(request), campaignId);
		apiKeyRequestAuthorizer.require(request, campaign.getProject().getId(), ApiKeyScope.CAMPAIGNS_READ);
		return CampaignResponse.from(campaign);
	}

	private Long projectId(HttpServletRequest request) {
		return apiKeyRequestAuthorizer.requireAuthenticated(request).projectId();
	}

	private int boundedLimit(int limit) {
		if (limit < 1 || limit > 100) throw new PublicApiException(400, "INVALID_REQUEST", "limit은 1~100 사이여야 합니다.");
		return limit;
	}
}

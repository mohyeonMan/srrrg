package link.srrrg.campaign.utm.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import link.srrrg.auth.request.service.ApiKeyRequestAuthorizer;
import link.srrrg.campaign.controller.CampaignController.CampaignResponse;
import link.srrrg.campaign.utm.service.CampaignUtmService;
import link.srrrg.campaign.utm.controller.CampaignUtmController.SelectTemplateRequest;
import link.srrrg.campaign.utm.controller.CampaignUtmController.UpdateUtmDefaultsRequest;
import link.srrrg.campaign.utm.model.CampaignUtmDefault;
import link.srrrg.project.apikey.model.ApiKeyScope;
import lombok.RequiredArgsConstructor;

/** API 키 표면에서 캠페인 UTM 선택과 기본값 계약을 담당한다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicCampaignUtmController {
	private final CampaignUtmService campaignUtmService;
	private final ApiKeyRequestAuthorizer authorizer;

	@PatchMapping("/campaigns/{campaignId}/utm-template")
	public CampaignResponse select(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestBody SelectTemplateRequest body) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		return CampaignResponse.from(campaignUtmService.selectTemplateForApiKey(projectId, campaignId, body.utmTemplateId()));
	}

	@GetMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> defaults(HttpServletRequest request, @PathVariable Long campaignId) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_READ);
		return toMap(campaignUtmService.defaultsForApiKey(projectId, campaignId));
	}

	@PatchMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> update(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestBody UpdateUtmDefaultsRequest body) {
		Long projectId = projectId(request);
		authorizer.require(request, projectId, ApiKeyScope.CAMPAIGNS_WRITE);
		campaignUtmService.updateDefaultsForApiKey(projectId, campaignId, body.defaultsOrEmpty());
		return toMap(campaignUtmService.defaultsForApiKey(projectId, campaignId));
	}

	private Long projectId(HttpServletRequest request) { return authorizer.requireAuthenticated(request).projectId(); }
	private Map<String, String> toMap(List<CampaignUtmDefault> defaults) {
		Map<String, String> result = new LinkedHashMap<>();
		for (CampaignUtmDefault value : defaults) result.put(value.getFieldName(), value.getDefaultValue());
		return result;
	}
}

package link.srrrg.campaign.utm.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.campaign.controller.CampaignController.CampaignResponse;
import link.srrrg.campaign.utm.service.CampaignUtmService;
import link.srrrg.campaign.utm.model.CampaignUtmDefault;
import lombok.RequiredArgsConstructor;

/** 캠페인의 UTM 템플릿 선택과 기본값 관리 계약을 담당한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class CampaignUtmController {
	private final CampaignUtmService campaignUtmService;

	@PatchMapping("/campaigns/{campaignId}/utm-template")
	public CampaignResponse selectTemplate(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestBody SelectTemplateRequest request) {
		return CampaignResponse.from(campaignUtmService.selectTemplate(principal.userId(), campaignId, request.utmTemplateId()));
	}

	@GetMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> defaults(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId) {
		return toMap(campaignUtmService.defaults(principal.userId(), campaignId));
	}

	@PatchMapping("/campaigns/{campaignId}/utm-defaults")
	public Map<String, String> updateDefaults(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestBody UpdateUtmDefaultsRequest request) {
		campaignUtmService.updateDefaults(principal.userId(), campaignId, request.defaultsOrEmpty());
		return toMap(campaignUtmService.defaults(principal.userId(), campaignId));
	}

	private Map<String, String> toMap(List<CampaignUtmDefault> defaults) {
		Map<String, String> result = new LinkedHashMap<>();
		for (CampaignUtmDefault value : defaults) result.put(value.getFieldName(), value.getDefaultValue());
		return result;
	}

	public record SelectTemplateRequest(Long utmTemplateId) { }
	public record UpdateUtmDefaultsRequest(Map<String, String> defaults) {
		public Map<String, String> defaultsOrEmpty() { return defaults == null ? Map.of() : defaults; }
	}
}

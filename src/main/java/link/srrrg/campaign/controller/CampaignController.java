package link.srrrg.campaign.controller;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.campaign.dto.UpdateCampaignRequest;
import link.srrrg.campaign.model.Campaign;
import link.srrrg.campaign.service.CampaignService;
import lombok.RequiredArgsConstructor;

/** 캠페인 자체의 생성, 조회, 수정과 삭제 계약을 담당한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class CampaignController {
	private final CampaignService campaigns;

	@PostMapping("/projects/{projectId}/campaigns")
	public ResponseEntity<CampaignResponse> create(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long projectId, @Valid @RequestBody CreateCampaignRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(CampaignResponse.from(campaigns.create(
				principal.userId(), projectId, request.name(), request.description(), request.defaultOriginalUrl())));
	}

	@GetMapping("/projects/{projectId}/campaigns")
	public CampaignPageResponse list(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		int boundedLimit = boundedLimit(limit);
		return CampaignPageResponse.of(campaigns.list(principal.userId(), projectId, cursor, boundedLimit + 1), boundedLimit);
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
		if (request.isDefaultOriginalUrlPresent()) {
			campaign = campaigns.changeDefaultOriginalUrl(principal.userId(), campaignId, request.getDefaultOriginalUrl());
		}
		return CampaignResponse.from(campaign);
	}

	@DeleteMapping("/campaigns/{campaignId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId) {
		campaigns.delete(principal.userId(), campaignId);
		return ResponseEntity.noContent().build();
	}

	private int boundedLimit(int limit) {
		if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit은 1~100 사이여야 합니다.");
		return limit;
	}

	public record CreateCampaignRequest(@NotBlank @Size(max = 100) String name,
			@Size(max = 500) String description, @Size(max = 2048) String defaultOriginalUrl) { }

	public record CampaignResponse(Long id, String name, String description, String defaultOriginalUrl,
			Long utmTemplateId, String utmTemplateName, Instant createdAt, Instant updatedAt) {
		public static CampaignResponse from(Campaign campaign) {
			return new CampaignResponse(campaign.getId(), campaign.getName(), campaign.getDescription(),
					campaign.getDefaultOriginalUrl(), campaign.getUtmTemplate() == null ? null : campaign.getUtmTemplate().getId(),
					campaign.getUtmTemplate() == null ? null : campaign.getUtmTemplate().getName(),
					campaign.getCreatedAt(), campaign.getUpdatedAt());
		}
	}

	public record CampaignPageResponse(List<CampaignResponse> items, Long nextCursor) {
		public static CampaignPageResponse of(List<Campaign> page, int limit) {
			List<Campaign> trimmed = page.size() > limit ? page.subList(0, limit) : page;
			Long nextCursor = page.size() > limit ? trimmed.getLast().getId() : null;
			return new CampaignPageResponse(trimmed.stream().map(CampaignResponse::from).toList(), nextCursor);
		}
	}
}

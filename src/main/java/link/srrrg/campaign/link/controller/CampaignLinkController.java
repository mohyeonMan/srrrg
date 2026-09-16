package link.srrrg.campaign.link.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.request.model.SrrrgPrincipal;
import link.srrrg.campaign.link.dto.CreateCampaignLinkRequest;
import link.srrrg.campaign.link.model.CampaignEffectiveUtmValue;
import link.srrrg.campaign.link.model.CampaignLinkQueryResult;
import link.srrrg.campaign.link.service.CampaignLinkCreationService;
import link.srrrg.campaign.link.service.CampaignLinkQueryService;
import link.srrrg.campaign.link.service.CampaignLinkManagementService;
import link.srrrg.link.model.Link;
import lombok.RequiredArgsConstructor;

/** 캠페인에 속한 링크의 발행, 조회와 일괄 삭제 계약을 담당한다. */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class CampaignLinkController {
	private final CampaignLinkCreationService linkCreation;
	private final CampaignLinkQueryService linkQueries;
	private final CampaignLinkManagementService linkManagement;

	@PostMapping("/campaigns/{campaignId}/links")
	public ResponseEntity<CampaignLinkResponse> create(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long campaignId, @Valid @RequestBody CreateCampaignLinkRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CampaignLinkResponse.from(linkCreation.createForUser(principal.userId(), campaignId, request)));
	}

	@GetMapping("/campaigns/{campaignId}/links")
	public WebCampaignLinkPageResponse list(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
		return WebCampaignLinkPageResponse.of(linkQueries.listForUser(principal.userId(), campaignId, cursor, boundedLimit(limit)));
	}

	@DeleteMapping("/campaigns/{campaignId}/links")
	public DeletedCampaignLinksResponse delete(@AuthenticationPrincipal SrrrgPrincipal principal,
			@PathVariable Long campaignId, @Valid @RequestBody DeleteCampaignLinksRequest request) {
		return new DeletedCampaignLinksResponse(linkManagement.delete(principal.userId(), campaignId, request.codes()));
	}

	private int boundedLimit(int limit) {
		if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit은 1~100 사이여야 합니다.");
		return limit;
	}

	public record DeleteCampaignLinksRequest(
			@NotNull @Size(min = 1, max = 100) List<@NotBlank @Pattern(regexp = "[0-9A-Za-z]{6}") String> codes) { }
	public record DeletedCampaignLinksResponse(int deletedCount) { }
	public record CampaignLinkResponse(String code, String name, String originalUrl, String externalId, Instant createdAt) {
		public static CampaignLinkResponse from(Link link) {
			return new CampaignLinkResponse(link.getCode(), link.getName(), link.getOriginalUrl(), link.getExternalId(), link.getCreatedAt());
		}
	}
	public record CampaignLinkPageResponse(List<CampaignLinkResponse> items, Long nextCursor) {
		public static CampaignLinkPageResponse of(CampaignLinkQueryResult result) {
			return new CampaignLinkPageResponse(result.items().stream().map(CampaignLinkResponse::from).toList(), result.nextCursor());
		}
	}
	public record EffectiveUtmResponse(String name, String value, String source) {
		static EffectiveUtmResponse from(CampaignEffectiveUtmValue value) {
			return new EffectiveUtmResponse(value.fieldName(), value.value(), value.source());
		}
	}
	public record WebCampaignLinkResponse(String code, String name, String originalUrl, String externalId,
			Instant createdAt, List<EffectiveUtmResponse> effectiveUtmValues) {
		static WebCampaignLinkResponse from(Link link, List<EffectiveUtmResponse> values) {
			return new WebCampaignLinkResponse(link.getCode(), link.getName(), link.getOriginalUrl(), link.getExternalId(),
					link.getCreatedAt(), values);
		}
	}
	public record WebCampaignLinkPageResponse(List<WebCampaignLinkResponse> items, Long nextCursor) {
		static WebCampaignLinkPageResponse of(CampaignLinkQueryResult result) {
			Map<Long, List<EffectiveUtmResponse>> byLinkId = new LinkedHashMap<>();
			for (CampaignEffectiveUtmValue value : result.effectiveUtmValues()) {
				byLinkId.computeIfAbsent(value.linkId(), ignored -> new ArrayList<>()).add(EffectiveUtmResponse.from(value));
			}
			return new WebCampaignLinkPageResponse(result.items().stream()
					.map(link -> WebCampaignLinkResponse.from(link, byLinkId.getOrDefault(link.getId(), List.of())))
					.toList(), result.nextCursor());
		}
	}
}

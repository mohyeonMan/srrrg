package link.srrrg.statistics;

import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.auth.SrrrgPrincipal;
import link.srrrg.campaign.Campaign;
import link.srrrg.campaign.CampaignRepository;
import link.srrrg.link.Link;
import link.srrrg.link.LinkNotFoundException;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.management.LinkManagementService;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.ApiKeyService.ApiKeyPrincipal;
import link.srrrg.project.ProjectMember;
import link.srrrg.project.ProjectMemberRepository;
import link.srrrg.statistics.StatisticsService.Bucket;

@RestController
public class StatisticsController {
	private static final String SECRET_KEY = "X-Srrrg-Secret-Key";
	private final StatisticsService statistics;
	private final LinkManagementService linkManagement;
	private final LinkRepository links;
	private final ProjectMemberRepository members;
	private final CampaignRepository campaigns;

	public StatisticsController(StatisticsService statistics, LinkManagementService linkManagement, LinkRepository links,
			ProjectMemberRepository members, CampaignRepository campaigns) {
		this.statistics = statistics; this.linkManagement = linkManagement; this.links = links;
		this.members = members; this.campaigns = campaigns;
	}

	@GetMapping("/api/links/{code}/statistics")
	public StatisticsResponse anonymous(@PathVariable String code, @RequestHeader(SECRET_KEY) String secret,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket) {
		linkManagement.getManagedLink(code, secret);
		Link link = links.findByCodeAndProjectIsNull(code).orElseThrow(LinkNotFoundException::new);
		return statistics.link(link.getId(), code, from, to, bucket);
	}

	@GetMapping("/api/web/projects/{projectId}/statistics")
	public StatisticsResponse project(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket) {
		ProjectMember member = member(principal.userId(), projectId);
		return statistics.project(projectId, member.getProject().getName(), from, to, bucket);
	}

	@GetMapping("/api/web/campaigns/{campaignId}/statistics")
	public StatisticsResponse campaign(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket) {
		Campaign campaign = campaign(campaignId);
		member(principal.userId(), campaign.getProject().getId());
		return statistics.campaign(campaignId, campaign.getName(), from, to, bucket);
	}

	@GetMapping("/api/web/projects/{projectId}/links/{code}/statistics")
	public StatisticsResponse projectLink(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@PathVariable String code, @RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "DAY") Bucket bucket) {
		member(principal.userId(), projectId);
		Link link = projectLink(projectId, code);
		return statistics.link(link.getId(), code, from, to, bucket);
	}

	@GetMapping("/api/v1/projects/{projectId}/statistics")
	public StatisticsResponse publicProject(HttpServletRequest request, @PathVariable Long projectId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket) {
		ApiKeyPrincipal key = apiKey(request, projectId);
		return statistics.project(projectId, "project-" + key.projectId(), from, to, bucket);
	}

	@GetMapping("/api/v1/campaigns/{campaignId}/statistics")
	public StatisticsResponse publicCampaign(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket) {
		ApiKeyPrincipal key = apiKey(request, null);
		Campaign campaign = campaign(campaignId);
		if (!campaign.getProject().getId().equals(key.projectId())) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		return statistics.campaign(campaignId, campaign.getName(), from, to, bucket);
	}

	@GetMapping("/api/v1/projects/{projectId}/links/{code}/statistics")
	public StatisticsResponse publicLink(HttpServletRequest request, @PathVariable Long projectId, @PathVariable String code,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket) {
		apiKey(request, projectId);
		Link link = projectLink(projectId, code);
		return statistics.link(link.getId(), code, from, to, bucket);
	}

	private ProjectMember member(Long userId, Long projectId) {
		ProjectMember member = members.findByIdProjectIdAndIdUserId(projectId, userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
		if (member.getProject().getArchivedAt() != null) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		return member;
	}
	private Link projectLink(Long projectId, String code) {
		Link link = links.findByProjectIdAndCode(projectId, code).orElseThrow(LinkNotFoundException::new);
		if (link.isDeleted()) throw new LinkNotFoundException();
		return link;
	}
	private Campaign campaign(Long campaignId) {
		return campaigns.findById(campaignId).orElseThrow(() -> new IllegalArgumentException("캠페인을 찾을 수 없습니다."));
	}
	private ApiKeyPrincipal apiKey(HttpServletRequest request, Long projectId) {
		ApiKeyPrincipal key = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (key == null) throw new SecurityException("유효한 API key가 필요합니다.");
		if (projectId != null && !key.projectId().equals(projectId)) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		if (!key.scopes().contains(ApiKeyScope.STATS_READ)) throw new SecurityException("stats:read scope가 필요합니다.");
		return key;
	}
}

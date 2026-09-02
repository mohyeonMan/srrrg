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
import link.srrrg.statistics.StatisticsResponse.Bucket;

/**
 * 통계 조회 엔드포인트. 세 API 표면의 경로가 이 한 클래스에 모여 있고, 인증 방식만 서로 다르다.
 *
 * <p>secret key로 익명 링크를, 쿠키 JWT로 프로젝트 자원을, API key로 같은 자원을 조회한다.
 * 집계 로직은 공유하고 인가만 경로별로 다르게 하므로, 새 경로를 추가할 때 인가 확인을 빠뜨리면
 * 그 경로만 무방비가 된다.</p>
 *
 * <p>이 클래스가 인가의 유일한 지점이다. {@code StatisticsService}는 식별자만 받고 권한을 보지 않는다.</p>
 */
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

	/**
	 * 익명 링크의 통계. secret key 대조를 링크 관리 서비스에 맡겨 인가를 처리한 뒤,
	 * 통계에 필요한 링크 id를 얻기 위해 한 번 더 조회한다. 대조에 실패하면 그 단계에서 404로 끝난다.
	 */
	@GetMapping("/api/links/{code}/statistics")
	public StatisticsResponse anonymous(@PathVariable String code, @RequestHeader(SECRET_KEY) String secret,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		linkManagement.getManagedLink(code, secret);
		Link link = links.findByCodeAndProjectIsNull(code).orElseThrow(LinkNotFoundException::new);
		return statistics.link(link.getId(), code, from, to, bucket, offset, limit);
	}

	@GetMapping("/api/web/projects/{projectId}/statistics")
	public StatisticsResponse project(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		ProjectMember member = member(principal.userId(), projectId);
		return statistics.project(projectId, member.getProject().getName(), from, to, bucket, offset, limit);
	}

	/**
	 * 캠페인 통계. 경로에 프로젝트가 없으므로 캠페인을 먼저 찾아 그 프로젝트에 대한 멤버십을 확인한다.
	 * 순서를 뒤집을 수 없는 구조라, 없는 캠페인은 404이고 남의 캠페인은 403으로 갈린다.
	 */
	@GetMapping("/api/web/campaigns/{campaignId}/statistics")
	public StatisticsResponse campaign(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		Campaign campaign = campaign(campaignId);
		member(principal.userId(), campaign.getProject().getId());
		return statistics.campaign(campaignId, campaign.getName(), from, to, bucket, offset, limit);
	}

	@GetMapping("/api/web/projects/{projectId}/links/{code}/statistics")
	public StatisticsResponse projectLink(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@PathVariable String code, @RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		member(principal.userId(), projectId);
		Link link = projectLink(projectId, code);
		return statistics.link(link.getId(), code, from, to, bucket, offset, limit);
	}

	@GetMapping("/api/v1/projects/{projectId}/statistics")
	public StatisticsResponse publicProject(HttpServletRequest request, @PathVariable Long projectId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		ApiKeyPrincipal key = apiKey(request, projectId);
		return statistics.project(projectId, "project-" + key.projectId(), from, to, bucket, offset, limit);
	}

	/**
	 * API key로 보는 캠페인 통계. 경로에 프로젝트 id가 없어 키 검사만으로는 범위를 좁힐 수 없다.
	 * 그래서 캠페인을 찾은 뒤 그 프로젝트가 키의 프로젝트와 같은지 직접 대조한다.
	 * 이 확인이 없으면 유효한 키 하나로 다른 프로젝트의 캠페인 성과를 볼 수 있다.
	 */
	@GetMapping("/api/v1/campaigns/{campaignId}/statistics")
	public StatisticsResponse publicCampaign(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		ApiKeyPrincipal key = apiKey(request, null);
		Campaign campaign = campaign(campaignId);
		if (!campaign.getProject().getId().equals(key.projectId())) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		return statistics.campaign(campaignId, campaign.getName(), from, to, bucket, offset, limit);
	}

	@GetMapping("/api/v1/projects/{projectId}/links/{code}/statistics")
	public StatisticsResponse publicLink(HttpServletRequest request, @PathVariable Long projectId, @PathVariable String code,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		apiKey(request, projectId);
		Link link = projectLink(projectId, code);
		return statistics.link(link.getId(), code, from, to, bucket, offset, limit);
	}

	/**
	 * 웹 경로의 인가. 멤버이기만 하면 되고 역할은 보지 않는다. 통계는 읽기 전용이라 VIEWER도 볼 수 있다.
	 */
	private ProjectMember member(Long userId, Long projectId) {
		return members.findActiveByProjectAndUser(projectId, userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
	}
	private Link projectLink(Long projectId, String code) {
		return links.findByProjectIdAndCode(projectId, code).orElseThrow(LinkNotFoundException::new);
	}
	private Campaign campaign(Long campaignId) {
		return campaigns.findById(campaignId)
				.orElseThrow(link.srrrg.campaign.CampaignNotFoundException::new);
	}
	/**
	 * API key 경로의 인가. 주체 존재, 프로젝트 일치, {@code stats:read} scope를 차례로 확인한다.
	 *
	 * @param projectId 경로에 프로젝트가 있으면 그 값, 없으면 {@code null}.
	 *                  {@code null}이면 프로젝트 일치 검사를 건너뛰므로 호출자가 다른 방법으로 범위를 확인해야 한다
	 */
	private ApiKeyPrincipal apiKey(HttpServletRequest request, Long projectId) {
		ApiKeyPrincipal key = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (key == null) throw new SecurityException("유효한 API key가 필요합니다.");
		if (projectId != null && !key.projectId().equals(projectId)) throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		if (!key.scopes().contains(ApiKeyScope.STATS_READ)) throw new SecurityException("stats:read scope가 필요합니다.");
		return key;
	}
}

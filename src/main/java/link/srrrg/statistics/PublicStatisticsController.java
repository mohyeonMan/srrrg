package link.srrrg.statistics;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.auth.ApiKeyRequestAuthorizer;
import link.srrrg.project.ApiKeyPrincipal;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.statistics.StatisticsResponse.Bucket;

/**
 * API key를 사용하는 공개 통계 HTTP 계약을 담당한다. 공통 authorizer에서 주체·프로젝트·scope를 확인하고,
 * 실제 통계 대상이 그 프로젝트에 속하는지는 {@link StatisticsService}가 조회와 함께 판정한다.
 */
@RestController
public class PublicStatisticsController {
	private final StatisticsService statisticsService;
	private final ApiKeyRequestAuthorizer apiKeyRequestAuthorizer;

	public PublicStatisticsController(StatisticsService statisticsService,
			ApiKeyRequestAuthorizer apiKeyRequestAuthorizer) {
		this.statisticsService = statisticsService;
		this.apiKeyRequestAuthorizer = apiKeyRequestAuthorizer;
	}

	@GetMapping("/api/v1/projects/{projectId}/statistics")
	public StatisticsResponse project(HttpServletRequest request, @PathVariable Long projectId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		ApiKeyPrincipal principal = apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.STATS_READ);
		return statisticsService.publicProject(principal, projectId, from, to, bucket, offset, limit);
	}

	@GetMapping("/api/v1/campaigns/{campaignId}/statistics")
	public StatisticsResponse campaign(HttpServletRequest request, @PathVariable Long campaignId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		ApiKeyPrincipal principal = apiKeyRequestAuthorizer.requireScope(request, ApiKeyScope.STATS_READ);
		return statisticsService.publicCampaign(principal, campaignId, from, to, bucket, offset, limit);
	}

	@GetMapping("/api/v1/projects/{projectId}/links/{code}/statistics")
	public StatisticsResponse projectLink(HttpServletRequest request, @PathVariable Long projectId, @PathVariable String code,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		apiKeyRequestAuthorizer.require(request, projectId, ApiKeyScope.STATS_READ);
		return statisticsService.publicProjectLink(projectId, code, from, to, bucket, offset, limit);
	}
}

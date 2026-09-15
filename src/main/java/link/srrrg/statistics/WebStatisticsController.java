package link.srrrg.statistics;

import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import link.srrrg.auth.SrrrgPrincipal;
import link.srrrg.statistics.StatisticsResponse.Bucket;

/**
 * 쿠키 JWT를 사용하는 웹 통계 HTTP 계약을 담당한다. 인증된 사용자 id만 application Service에 전달하고,
 * 프로젝트 멤버십과 대상 리소스의 소유 범위는 Service가 판정한다.
 */
@RestController
public class WebStatisticsController {
	private final StatisticsService statisticsService;

	public WebStatisticsController(StatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}

	@GetMapping("/api/web/projects/{projectId}/statistics")
	public StatisticsResponse project(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		return statisticsService.webProject(principal.userId(), projectId, from, to, bucket, offset, limit);
	}

	@GetMapping("/api/web/campaigns/{campaignId}/statistics")
	public StatisticsResponse campaign(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long campaignId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		return statisticsService.webCampaign(principal.userId(), campaignId, from, to, bucket, offset, limit);
	}

	@GetMapping("/api/web/projects/{projectId}/links/{code}/statistics")
	public StatisticsResponse projectLink(@AuthenticationPrincipal SrrrgPrincipal principal, @PathVariable Long projectId,
			@PathVariable String code, @RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		return statisticsService.webProjectLink(principal.userId(), projectId, code, from, to, bucket, offset, limit);
	}
}

package link.srrrg.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import link.srrrg.link.access.LinkAccessEvent.Outcome;

/**
 * 통계 리포트 응답 전체. 화면이 추가 계산 없이 그대로 그릴 수 있는 형태로 맞춘 값이다.
 *
 * <p>모든 수치가 전체·사람·봇으로 나뉘고, 접근 수와 실제 이동 수가 따로 있다. 봇 트래픽이 성과를
 * 부풀리는 것과, 유입은 있었지만 만료·차단으로 이동하지 못한 경우를 구분해 보기 위해서다.</p>
 *
 * <p>범위에 따라 채워지는 목록이 다르다. 해당 없는 목록은 비어 있는 페이지로 나간다.</p>
 */
public record StatisticsResponse(
		Scope scope, String name, LocalDate from, LocalDate to, Bucket bucket,
		Summary summary, List<TrendPoint> trend, List<OutcomeBreakdown> outcomes,
		List<Breakdown> referrers, List<Breakdown> devices, List<Breakdown> browsers,
		List<Breakdown> operatingSystems, List<RecentActivity> recentActivity,
		PageResult<LinkRow> links, PageResult<UtmRow> utm, PageResult<CampaignRow> campaigns) {

	public enum Scope { LINK, CAMPAIGN, PROJECT }
	public enum Bucket { DAY, MONTH, YEAR }
	public enum LinkActivityStatus { HUMAN_ACCESSED, BOT_ONLY, NO_ACCESS }
	public enum DestinationSource { LINK, CAMPAIGN_DEFAULT }

	public record Summary(PeriodMetrics current, PeriodMetrics previous, LifetimeLinkSummary lifetimeLinks) { }
	public record PeriodMetrics(long entries, long redirects, long humanEntries, long humanRedirects,
			long botEntries, long botRedirects, long nonRedirects) { }
	public record LifetimeLinkSummary(long total, long humanAccessed, long botOnly, long noAccess,
			long standalone, long campaign) { }
	public record TrendPoint(LocalDate periodStart, long entries, long redirects,
			long humanEntries, long humanRedirects, long botEntries, long botRedirects) { }
	public record Breakdown(String name, long count, double share) { }
	public record OutcomeBreakdown(Outcome outcome, long count, double share) { }
	public record RecentActivity(Instant accessedAt, Outcome outcome, String referrerDomain,
			String device, String browser) { }
	public record LinkRow(String code, String externalId, DestinationSource destinationSource,
			LinkActivityStatus lifetimeStatus, PeriodMetrics period,
			Instant firstAccessedAt, Instant lastAccessedAt) { }
	public record UtmRow(String field, String value, long configuredLinks, long lifetimeRedirectedLinks,
			long lifetimeBotOnlyRedirectedLinks, long redirectedEvents) { }
	public record CampaignRow(Long id, String name, long links, PeriodMetrics period) { }
	public record PageResult<T>(List<T> items, Integer nextOffset, long totalItems) {
		public static <T> PageResult<T> empty() { return new PageResult<>(List.of(), null, 0); }
	}
}

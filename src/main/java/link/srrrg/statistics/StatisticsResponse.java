package link.srrrg.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import link.srrrg.link.access.LinkAccessEvent.Outcome;

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

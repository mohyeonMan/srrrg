package link.srrrg.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StatisticsResponse(
		String scope, String name, LocalDate from, LocalDate to, String bucket,
		Summary summary, List<TrendPoint> trend, List<Breakdown> outcomes,
		List<Breakdown> referrers, List<Breakdown> devices, List<Breakdown> browsers,
		List<Breakdown> operatingSystems, List<RecentActivity> recentActivity,
		List<LinkRow> links, List<UtmRow> utm, List<CampaignRow> campaigns) {

	public record Summary(long entries, long redirects, long nonRedirects, long bots,
			long previousEntries, long previousRedirects, long totalLinks,
			long checkedLinks, long botOnlyLinks, long uncheckedLinks,
			long standaloneLinks, long campaignLinks) { }
	public record TrendPoint(String date, long entries, long redirects, long bots) { }
	public record Breakdown(String name, long count, double share) { }
	public record RecentActivity(Instant accessedAt, String outcome, String referrerDomain,
			String device, String browser) { }
	public record LinkRow(String code, String externalId, String destinationSource, String status,
			long entries, long redirects, Instant firstAccessedAt, Instant lastAccessedAt) { }
	public record UtmRow(String field, String value, long links, long accessedLinks,
			long botOnlyLinks, long entries, long redirects) { }
	public record CampaignRow(Long id, String name, long links, long entries, long redirects) { }
}

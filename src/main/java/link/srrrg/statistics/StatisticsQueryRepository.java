package link.srrrg.statistics;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import link.srrrg.link.access.LinkAccessEvent.Outcome;
import link.srrrg.statistics.StatisticsResponse.Breakdown;
import link.srrrg.statistics.StatisticsResponse.CampaignRow;
import link.srrrg.statistics.StatisticsResponse.DestinationSource;
import link.srrrg.statistics.StatisticsResponse.LinkActivityStatus;
import link.srrrg.statistics.StatisticsResponse.LinkRow;
import link.srrrg.statistics.StatisticsResponse.OutcomeBreakdown;
import link.srrrg.statistics.StatisticsResponse.PageResult;
import link.srrrg.statistics.StatisticsResponse.PeriodMetrics;
import link.srrrg.statistics.StatisticsResponse.RecentActivity;
import link.srrrg.statistics.StatisticsResponse.TrendPoint;
import link.srrrg.statistics.StatisticsResponse.UtmRow;

@Repository
class StatisticsQueryRepository {
	private final JdbcTemplate jdbc;

	StatisticsQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	TotalsWindow totals(StatisticsQueryScope scope, StatisticsPeriod period) {
		String sql = """
				SELECT
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<?),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.outcome='REDIRECTED'),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND NOT e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.outcome='REDIRECTED' AND NOT e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.outcome='REDIRECTED' AND e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<?),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.outcome='REDIRECTED'),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND NOT e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.outcome='REDIRECTED' AND NOT e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.accessed_at>=? AND e.accessed_at<? AND e.outcome='REDIRECTED' AND e.is_bot)
				FROM links l LEFT JOIN link_access_events e ON e.link_id=l.id AND e.accessed_at>=? AND e.accessed_at<?
				WHERE %s
				""".formatted(scope.predicate());
		Instant start = period.start(), end = period.end(), previous = period.previousStart();
		Object[] args = new Object[] {
			timestamp(start),timestamp(end), timestamp(start),timestamp(end), timestamp(start),timestamp(end),
			timestamp(start),timestamp(end), timestamp(start),timestamp(end), timestamp(start),timestamp(end),
			timestamp(previous),timestamp(start), timestamp(previous),timestamp(start), timestamp(previous),timestamp(start),
			timestamp(previous),timestamp(start), timestamp(previous),timestamp(start), timestamp(previous),timestamp(start),
			timestamp(previous),timestamp(end), scope.id()
		};
		return jdbc.queryForObject(sql, (rs, row) -> new TotalsWindow(
				metrics(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)),
				metrics(rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getLong(10), rs.getLong(11), rs.getLong(12))), args);
	}

	long[] lifetimeLinkSummary(StatisticsQueryScope scope) {
		String sql = """
				SELECT COUNT(*),
				 COUNT(*) FILTER (WHERE human_count>0),
				 COUNT(*) FILTER (WHERE human_count=0 AND event_count>0),
				 COUNT(*) FILTER (WHERE event_count=0),
				 COUNT(*) FILTER (WHERE campaign_id IS NULL),
				 COUNT(*) FILTER (WHERE campaign_id IS NOT NULL)
				FROM (
				 SELECT l.id,l.campaign_id,COUNT(e.id) event_count,
				        COUNT(e.id) FILTER (WHERE NOT e.is_bot) human_count
				 FROM links l LEFT JOIN link_access_events e ON e.link_id=l.id
				 WHERE %s GROUP BY l.id
				) scoped
				""".formatted(scope.predicate());
		return jdbc.queryForObject(sql, (rs, row) -> new long[] {
			rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6)
		}, scope.id());
	}

	List<RawTrendPoint> trend(StatisticsQueryScope scope, StatisticsPeriod period, StatisticsResponse.Bucket bucket) {
		String unit = bucket.name().toLowerCase(Locale.ROOT);
		String sql = """
				SELECT date_trunc('%s',e.accessed_at AT TIME ZONE 'Asia/Seoul')::date,
				 COUNT(*),COUNT(*) FILTER (WHERE e.outcome='REDIRECTED'),
				 COUNT(*) FILTER (WHERE NOT e.is_bot),
				 COUNT(*) FILTER (WHERE e.outcome='REDIRECTED' AND NOT e.is_bot),
				 COUNT(*) FILTER (WHERE e.is_bot),
				 COUNT(*) FILTER (WHERE e.outcome='REDIRECTED' AND e.is_bot)
				FROM link_access_events e JOIN links l ON l.id=e.link_id
				WHERE e.accessed_at>=? AND e.accessed_at<? AND %s
				GROUP BY 1 ORDER BY 1
				""".formatted(unit, scope.predicate());
		return jdbc.query(sql, (rs, row) -> new RawTrendPoint(rs.getDate(1).toLocalDate(),
				rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getLong(7)),
				timestamp(period.start()), timestamp(period.end()), scope.id());
	}

	List<OutcomeBreakdown> outcomes(StatisticsQueryScope scope, StatisticsPeriod period) {
		String expression = "e.outcome";
		String sql = breakdownSql(scope, expression);
		List<Map<String,Object>> rows = jdbc.queryForList(sql, timestamp(period.start()), timestamp(period.end()), scope.id());
		return rows.stream().map(row -> new OutcomeBreakdown(Outcome.valueOf(String.valueOf(row.get("name"))),
				number(row,"count"), share(row))).toList();
	}

	List<Breakdown> breakdown(StatisticsQueryScope scope, StatisticsPeriod period, Dimension dimension) {
		String expression = switch (dimension) {
			case REFERRER -> "CASE WHEN e.referer IS NULL OR btrim(e.referer)='' THEN '직접 유입' ELSE COALESCE(lower(substring(e.referer from '^[A-Za-z][A-Za-z0-9+.-]*://([^/?#]+)')),'기타') END";
			case DEVICE -> "COALESCE(e.device_type,'Unknown')";
			case BROWSER -> "COALESCE(e.browser_name,'Unknown')";
			case OPERATING_SYSTEM -> "COALESCE(e.os_name,'Unknown')";
		};
		List<Map<String,Object>> rows = jdbc.queryForList(breakdownSql(scope, expression),
				timestamp(period.start()), timestamp(period.end()), scope.id());
		return rows.stream().map(row -> new Breakdown(String.valueOf(row.get("name")), number(row,"count"), share(row))).toList();
	}

	List<RecentActivity> recent(StatisticsQueryScope scope, StatisticsPeriod period) {
		String sql = "SELECT e.accessed_at,e.outcome,e.referer,COALESCE(e.device_type,'Unknown'),COALESCE(e.browser_name,'Unknown') "
				+ "FROM link_access_events e JOIN links l ON l.id=e.link_id WHERE e.accessed_at>=? AND e.accessed_at<? AND "
				+ scope.predicate() + " ORDER BY e.accessed_at DESC LIMIT 20";
		return jdbc.query(sql, (rs,row) -> new RecentActivity(rs.getTimestamp(1).toInstant(), Outcome.valueOf(rs.getString(2)),
				referrerDomain(rs.getString(3)),rs.getString(4),rs.getString(5)),
				timestamp(period.start()),timestamp(period.end()),scope.id());
	}

	PageResult<LinkRow> campaignLinks(long campaignId, StatisticsPeriod period, int offset, int limit) {
		String sql = """
				WITH scoped_links AS (
				 SELECT * FROM links WHERE campaign_id=? AND deleted_at IS NULL
				), lifetime AS (
				 SELECT e.link_id,COUNT(*) events,COUNT(*) FILTER (WHERE NOT e.is_bot) humans,
				        MIN(e.accessed_at) first_at,MAX(e.accessed_at) last_at
				 FROM link_access_events e JOIN scoped_links l ON l.id=e.link_id GROUP BY e.link_id
				), period AS (
				 SELECT e.link_id,COUNT(*) entries,COUNT(*) FILTER (WHERE e.outcome='REDIRECTED') redirects,
				        COUNT(*) FILTER (WHERE NOT e.is_bot) human_entries,
				        COUNT(*) FILTER (WHERE e.outcome='REDIRECTED' AND NOT e.is_bot) human_redirects,
				        COUNT(*) FILTER (WHERE e.is_bot) bot_entries,
				        COUNT(*) FILTER (WHERE e.outcome='REDIRECTED' AND e.is_bot) bot_redirects
				 FROM link_access_events e JOIN scoped_links l ON l.id=e.link_id
				 WHERE e.accessed_at>=? AND e.accessed_at<? GROUP BY e.link_id
				)
				SELECT l.code,l.external_id,l.original_url IS NULL,
				 COALESCE(lifetime.events,0),COALESCE(lifetime.humans,0),
				 COALESCE(period.entries,0),COALESCE(period.redirects,0),
				 COALESCE(period.human_entries,0),COALESCE(period.human_redirects,0),
				 COALESCE(period.bot_entries,0),COALESCE(period.bot_redirects,0),
				 lifetime.first_at,lifetime.last_at,COUNT(*) OVER() total
				FROM scoped_links l LEFT JOIN lifetime ON lifetime.link_id=l.id LEFT JOIN period ON period.link_id=l.id
				ORDER BY COALESCE(period.entries,0) DESC,l.id DESC LIMIT ? OFFSET ?
				""";
		List<LinkPageRow> rows = jdbc.query(sql, (rs,row) -> new LinkPageRow(new LinkRow(
				rs.getString(1),rs.getString(2),rs.getBoolean(3)?DestinationSource.CAMPAIGN_DEFAULT:DestinationSource.LINK,
				status(rs.getLong(4),rs.getLong(5)),metrics(rs.getLong(6),rs.getLong(7),rs.getLong(8),rs.getLong(9),rs.getLong(10),rs.getLong(11)),
				instant(rs.getTimestamp(12)),instant(rs.getTimestamp(13))),rs.getLong(14)),
				campaignId,timestamp(period.start()),timestamp(period.end()),limit,offset);
		return page(rows.stream().map(LinkPageRow::item).toList(), rows.isEmpty()?0:rows.getFirst().total(), offset, limit);
	}

	PageResult<UtmRow> campaignUtm(long campaignId, StatisticsPeriod period, int offset, int limit) {
		String sql = """
				WITH campaign_scope AS (
				 SELECT id,utm_template_id FROM campaigns WHERE id=? AND deleted_at IS NULL
				), active_fields AS (
				 SELECT field.name FROM campaign_scope campaign
				 JOIN utm_template_fields field ON field.utm_template_id=campaign.utm_template_id AND field.deleted_at IS NULL
				), scoped_links AS (
				 SELECT link.* FROM links link JOIN campaign_scope campaign ON campaign.id=link.campaign_id WHERE link.deleted_at IS NULL
				), current_config AS (
				 SELECT field.name field_name,COALESCE(value.value,defaults.default_value,'(없음)') field_value,COUNT(*) links
				 FROM scoped_links link CROSS JOIN active_fields field
				 LEFT JOIN link_utm_values value ON value.link_id=link.id AND value.field_name=field.name
				 LEFT JOIN campaign_utm_defaults defaults ON defaults.campaign_id=link.campaign_id AND defaults.field_name=field.name
				 GROUP BY field.name,COALESCE(value.value,defaults.default_value,'(없음)')
				), all_per_link AS (
				 SELECT field.name field_name,COALESCE(event.effective_utm->>field.name,'(없음)') field_value,event.link_id,
				        COUNT(*) FILTER (WHERE NOT event.is_bot) humans,COUNT(*) events
				 FROM link_access_events event JOIN scoped_links link ON link.id=event.link_id CROSS JOIN active_fields field
				 WHERE event.outcome='REDIRECTED'
				 GROUP BY field.name,COALESCE(event.effective_utm->>field.name,'(없음)'),event.link_id
				), all_stats AS (
				 SELECT field_name,field_value,COUNT(*) redirected_links,
				        COUNT(*) FILTER (WHERE humans=0 AND events>0) bot_only_redirected_links
				 FROM all_per_link GROUP BY field_name,field_value
				), period_stats AS (
				 SELECT field.name field_name,COALESCE(event.effective_utm->>field.name,'(없음)') field_value,COUNT(*) redirected_events
				 FROM link_access_events event JOIN scoped_links link ON link.id=event.link_id CROSS JOIN active_fields field
				 WHERE event.outcome='REDIRECTED' AND event.accessed_at>=? AND event.accessed_at<?
				 GROUP BY field.name,COALESCE(event.effective_utm->>field.name,'(없음)')
				), keys AS (
				 SELECT field_name,field_value FROM current_config UNION SELECT field_name,field_value FROM all_stats
				)
				SELECT keys.field_name,keys.field_value,COALESCE(config.links,0),
				 COALESCE(all_stats.redirected_links,0),COALESCE(all_stats.bot_only_redirected_links,0),
				 COALESCE(period.redirected_events,0),COUNT(*) OVER() total
				FROM keys LEFT JOIN current_config config USING(field_name,field_value)
				LEFT JOIN all_stats USING(field_name,field_value) LEFT JOIN period_stats period USING(field_name,field_value)
				ORDER BY 6 DESC,keys.field_name,keys.field_value LIMIT ? OFFSET ?
				""";
		List<UtmPageRow> rows = jdbc.query(sql, (rs,row) -> new UtmPageRow(new UtmRow(rs.getString(1),rs.getString(2),
				rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6)),rs.getLong(7)),
				campaignId,timestamp(period.start()),timestamp(period.end()),limit,offset);
		return page(rows.stream().map(UtmPageRow::item).toList(), rows.isEmpty()?0:rows.getFirst().total(), offset, limit);
	}

	PageResult<CampaignRow> projectCampaigns(long projectId, StatisticsPeriod period, int offset, int limit) {
		String sql = """
				SELECT c.id,c.name,COUNT(DISTINCT l.id),COUNT(e.id),
				 COUNT(e.id) FILTER (WHERE e.outcome='REDIRECTED'),
				 COUNT(e.id) FILTER (WHERE NOT e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.outcome='REDIRECTED' AND NOT e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.is_bot),
				 COUNT(e.id) FILTER (WHERE e.outcome='REDIRECTED' AND e.is_bot),COUNT(*) OVER() total
				FROM campaigns c LEFT JOIN links l ON l.campaign_id=c.id AND l.deleted_at IS NULL
				LEFT JOIN link_access_events e ON e.link_id=l.id AND e.accessed_at>=? AND e.accessed_at<?
				WHERE c.project_id=? AND c.deleted_at IS NULL GROUP BY c.id
				ORDER BY 4 DESC,c.id DESC LIMIT ? OFFSET ?
				""";
		List<CampaignPageRow> rows = jdbc.query(sql, (rs,row) -> new CampaignPageRow(new CampaignRow(rs.getLong(1),rs.getString(2),rs.getLong(3),
				metrics(rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getLong(7),rs.getLong(8),rs.getLong(9))),rs.getLong(10)),
				timestamp(period.start()),timestamp(period.end()),projectId,limit,offset);
		return page(rows.stream().map(CampaignPageRow::item).toList(), rows.isEmpty()?0:rows.getFirst().total(), offset, limit);
	}

	private String breakdownSql(StatisticsQueryScope scope, String expression) {
		return "SELECT name,count,SUM(count) OVER() total FROM (SELECT " + expression
				+ " name,COUNT(*) count FROM link_access_events e JOIN links l ON l.id=e.link_id WHERE e.accessed_at>=? "
				+ "AND e.accessed_at<? AND " + scope.predicate() + " GROUP BY 1) grouped ORDER BY count DESC LIMIT 10";
	}

	private PeriodMetrics metrics(long entries,long redirects,long humanEntries,long humanRedirects,long botEntries,long botRedirects) {
		return new PeriodMetrics(entries,redirects,humanEntries,humanRedirects,botEntries,botRedirects,entries-redirects);
	}
	private <T> PageResult<T> page(List<T> items,long total,int offset,int limit) {
		return new PageResult<>(items,offset+items.size()<total?offset+limit:null,total);
	}
	private long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
	private double share(Map<String,Object> row) { long total=number(row,"total"); return total==0?0:number(row,"count")*100.0/total; }
	private LinkActivityStatus status(long events,long humans) { return humans>0?LinkActivityStatus.HUMAN_ACCESSED:events>0?LinkActivityStatus.BOT_ONLY:LinkActivityStatus.NO_ACCESS; }
	private Timestamp timestamp(Instant value) { return Timestamp.from(value); }
	private Instant instant(Timestamp value) { return value==null?null:value.toInstant(); }
	private String referrerDomain(String value) {
		if(value==null||value.isBlank()) return "직접 유입";
		try { String host=URI.create(value).getHost(); return host==null?"기타":host.toLowerCase(Locale.ROOT); }
		catch(IllegalArgumentException exception) { return "기타"; }
	}

	enum Dimension { REFERRER, DEVICE, BROWSER, OPERATING_SYSTEM }
	record TotalsWindow(PeriodMetrics current,PeriodMetrics previous) { }
	record RawTrendPoint(LocalDate date,long entries,long redirects,long humanEntries,long humanRedirects,long botEntries,long botRedirects) {
		TrendPoint toResponse() { return new TrendPoint(date,entries,redirects,humanEntries,humanRedirects,botEntries,botRedirects); }
	}
	record LinkPageRow(LinkRow item,long total) { }
	record UtmPageRow(UtmRow item,long total) { }
	record CampaignPageRow(CampaignRow item,long total) { }
}

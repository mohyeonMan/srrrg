package link.srrrg.statistics;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import link.srrrg.statistics.StatisticsResponse.Breakdown;
import link.srrrg.statistics.StatisticsResponse.CampaignRow;
import link.srrrg.statistics.StatisticsResponse.LinkRow;
import link.srrrg.statistics.StatisticsResponse.RecentActivity;
import link.srrrg.statistics.StatisticsResponse.Summary;
import link.srrrg.statistics.StatisticsResponse.TrendPoint;
import link.srrrg.statistics.StatisticsResponse.UtmRow;

@Service
public class StatisticsService {
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private final JdbcTemplate jdbc;

	public StatisticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public StatisticsResponse link(Long linkId, String name, LocalDate from, LocalDate to, Bucket bucket) {
		return report("LINK", name, "l.id = ? AND NOT l.is_deleted", new Object[] {linkId}, from, to, bucket, List.of(), List.of(), List.of());
	}

	public StatisticsResponse campaign(Long campaignId, String name, LocalDate from, LocalDate to, Bucket bucket) {
		Period p = period(from, to);
		return report("CAMPAIGN", name, "l.campaign_id = ? AND NOT l.is_deleted", new Object[] {campaignId}, from, to, bucket,
				linkRows(campaignId, p), utmRows(campaignId, p), List.of());
	}

	public StatisticsResponse project(Long projectId, String name, LocalDate from, LocalDate to, Bucket bucket) {
		Period p = period(from, to);
		return report("PROJECT", name, "l.project_id = ? AND NOT l.is_deleted", new Object[] {projectId}, from, to, bucket,
				List.of(), List.of(), campaignRows(projectId, p));
	}

	private StatisticsResponse report(String scope, String name, String scopeSql, Object[] scopeArgs,
			LocalDate requestedFrom, LocalDate requestedTo, Bucket bucket,
			List<LinkRow> links, List<UtmRow> utm, List<CampaignRow> campaigns) {
		Period p = period(requestedFrom, requestedTo);
		long[] current = totals(scopeSql, scopeArgs, p.start, p.end);
		long[] previous = totals(scopeSql, scopeArgs, p.previousStart, p.start);
		long[] statuses = linkStatuses(scopeSql, scopeArgs);
		Summary summary = new Summary(current[0], current[1], current[0] - current[1], current[2],
				previous[0], previous[1], statuses[0], statuses[1], statuses[2], statuses[3], statuses[4], statuses[5]);
		return new StatisticsResponse(scope, name, p.from, p.to, bucket.name(), summary,
				trend(scopeSql, scopeArgs, p, bucket), breakdown(scopeSql, scopeArgs, p, "e.outcome"),
				referrers(scopeSql, scopeArgs, p), breakdown(scopeSql, scopeArgs, p, "COALESCE(e.device_type, 'Unknown')"),
				breakdown(scopeSql, scopeArgs, p, "COALESCE(e.browser_name, 'Unknown')"),
				breakdown(scopeSql, scopeArgs, p, "COALESCE(e.os_name, 'Unknown')"),
				recent(scopeSql, scopeArgs, p), links, utm, campaigns);
	}

	private long[] totals(String scopeSql, Object[] scopeArgs, Instant start, Instant end) {
		String sql = """
				SELECT COUNT(e.id), COUNT(e.id) FILTER (WHERE e.outcome = 'REDIRECTED'),
				       COUNT(e.id) FILTER (WHERE e.is_bot)
				  FROM links l LEFT JOIN link_access_events e ON e.link_id = l.id
				   AND e.accessed_at >= ? AND e.accessed_at < ? WHERE
				""" + scopeSql;
		Object[] args = args(start, end, scopeArgs);
		return jdbc.queryForObject(sql, (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, args);
	}

	private long[] linkStatuses(String scopeSql, Object[] scopeArgs) {
		String sql = """
				SELECT COUNT(*), COUNT(*) FILTER (WHERE human_count > 0),
				       COUNT(*) FILTER (WHERE human_count = 0 AND event_count > 0),
				       COUNT(*) FILTER (WHERE event_count = 0),
				       COUNT(*) FILTER (WHERE campaign_id IS NULL), COUNT(*) FILTER (WHERE campaign_id IS NOT NULL)
				FROM (SELECT l.id,l.campaign_id,COUNT(e.id) event_count,
				             COUNT(e.id) FILTER (WHERE NOT e.is_bot) human_count
				        FROM links l LEFT JOIN link_access_events e ON e.link_id = l.id
				       WHERE
				""" + scopeSql + " GROUP BY l.id) scoped";
		return jdbc.queryForObject(sql, (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)}, scopeArgs);
	}

	private List<TrendPoint> trend(String scopeSql, Object[] scopeArgs, Period p, Bucket bucket) {
		String unit = bucket.name().toLowerCase(Locale.ROOT);
		String sql = ("""
				SELECT date_trunc('%s', e.accessed_at AT TIME ZONE 'Asia/Seoul')::text period,
				       COUNT(*), COUNT(*) FILTER (WHERE e.outcome = 'REDIRECTED'), COUNT(*) FILTER (WHERE e.is_bot)
				  FROM link_access_events e JOIN links l ON l.id = e.link_id
				 WHERE e.accessed_at >= ? AND e.accessed_at < ? AND %s GROUP BY 1 ORDER BY 1
				""").formatted(unit, scopeSql);
		List<TrendPoint> found = jdbc.query(sql, (rs, row) -> new TrendPoint(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
				args(p.start, p.end, scopeArgs));
		Map<LocalDate, TrendPoint> byDate = found.stream().collect(java.util.stream.Collectors.toMap(
				point -> LocalDate.parse(point.date().substring(0, 10)), point -> point));
		List<TrendPoint> complete = new java.util.ArrayList<>();
		LocalDate cursor = bucket == Bucket.DAY ? p.from : bucket == Bucket.MONTH ? p.from.withDayOfMonth(1) : p.from.withDayOfYear(1);
		while (!cursor.isAfter(p.to)) {
			TrendPoint point = byDate.get(cursor);
			complete.add(point == null ? new TrendPoint(cursor.toString(), 0, 0, 0) : point);
			cursor = bucket == Bucket.DAY ? cursor.plusDays(1) : bucket == Bucket.MONTH ? cursor.plusMonths(1) : cursor.plusYears(1);
		}
		return complete;
	}

	private List<Breakdown> breakdown(String scopeSql, Object[] scopeArgs, Period p, String expression) {
		String sql = "SELECT name,count,SUM(count) OVER() total FROM (SELECT " + expression
				+ " name,COUNT(*) count FROM link_access_events e JOIN links l ON l.id=e.link_id WHERE e.accessed_at>=? "
				+ "AND e.accessed_at<? AND " + scopeSql + " GROUP BY 1) grouped ORDER BY count DESC LIMIT 10";
		List<Map<String, Object>> rows = jdbc.queryForList(sql, args(p.start, p.end, scopeArgs));
		return rows.stream().map(row -> item(String.valueOf(row.get("name")), ((Number) row.get("count")).longValue(),
				((Number) row.get("total")).longValue())).toList();
	}

	private List<Breakdown> referrers(String scopeSql, Object[] scopeArgs, Period p) {
		String domain = "CASE WHEN e.referer IS NULL OR btrim(e.referer)='' THEN '직접 유입' ELSE COALESCE(lower(substring(e.referer from '^[A-Za-z][A-Za-z0-9+.-]*://([^/?#]+)')),'기타') END";
		return breakdown(scopeSql, scopeArgs, p, domain);
	}

	private List<RecentActivity> recent(String scopeSql, Object[] scopeArgs, Period p) {
		String sql = "SELECT e.accessed_at,e.outcome,e.referer,COALESCE(e.device_type,'Unknown'),COALESCE(e.browser_name,'Unknown') "
				+ "FROM link_access_events e JOIN links l ON l.id=e.link_id WHERE e.accessed_at >= ? AND e.accessed_at < ? AND "
				+ scopeSql + " ORDER BY e.accessed_at DESC LIMIT 20";
		return jdbc.query(sql, (rs, row) -> new RecentActivity(rs.getTimestamp(1).toInstant(), rs.getString(2),
				referrerDomain(rs.getString(3)), rs.getString(4), rs.getString(5)), args(p.start, p.end, scopeArgs));
	}

	private List<LinkRow> linkRows(Long campaignId, Period p) {
		String sql = """
				SELECT l.code,l.external_id,CASE WHEN l.original_url IS NULL THEN 'CAMPAIGN_DEFAULT' ELSE 'OWN' END,
				       COALESCE(a.events,0),COALESCE(a.humans,0),COALESCE(p.events,0),COALESCE(p.redirects,0),a.first_at,a.last_at
				  FROM links l LEFT JOIN LATERAL (
				    SELECT COUNT(*) events,COUNT(*) FILTER (WHERE NOT is_bot) humans,MIN(accessed_at) first_at,MAX(accessed_at) last_at
				      FROM link_access_events WHERE link_id=l.id
				  ) a ON true LEFT JOIN LATERAL (
				    SELECT COUNT(*) events,COUNT(*) FILTER (WHERE outcome='REDIRECTED') redirects
				      FROM link_access_events WHERE link_id=l.id AND accessed_at>=? AND accessed_at<?
				  ) p ON true
				 WHERE l.campaign_id=? AND NOT l.is_deleted ORDER BY COALESCE(p.events,0) DESC,l.id DESC LIMIT 500
				""";
		return jdbc.query(sql, (rs, row) -> new LinkRow(rs.getString(1), rs.getString(2), rs.getString(3),
				status(rs.getLong(4), rs.getLong(5)), rs.getLong(6), rs.getLong(7),
				instant(rs.getTimestamp(8)), instant(rs.getTimestamp(9))), timestamp(p.start), timestamp(p.end), campaignId);
	}

	private List<UtmRow> utmRows(Long campaignId, Period p) {
		String sql = """
				WITH campaign_scope AS (
				  SELECT id,utm_template_id FROM campaigns WHERE id=?
				), active_fields AS (
				  SELECT field.name FROM campaign_scope campaign
				  JOIN utm_template_fields field ON field.utm_template_id=campaign.utm_template_id
				   AND field.deleted_at IS NULL
				), scoped_links AS (
				  SELECT link.* FROM links link JOIN campaign_scope campaign ON campaign.id=link.campaign_id
				   WHERE NOT link.is_deleted
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
				  SELECT field_name,field_value,COUNT(*) accessed_links,COUNT(*) FILTER (WHERE humans=0 AND events>0) bot_only_links
				    FROM all_per_link GROUP BY field_name,field_value
				), period_stats AS (
				  SELECT field.name field_name,COALESCE(event.effective_utm->>field.name,'(없음)') field_value,COUNT(*) redirects
				    FROM link_access_events event JOIN scoped_links link ON link.id=event.link_id CROSS JOIN active_fields field
				   WHERE event.outcome='REDIRECTED' AND event.accessed_at>=? AND event.accessed_at<?
				   GROUP BY field.name,COALESCE(event.effective_utm->>field.name,'(없음)')
				), keys AS (
				  SELECT field_name,field_value FROM current_config UNION SELECT field_name,field_value FROM all_stats
				)
				SELECT keys.field_name,keys.field_value,COALESCE(config.links,0),COALESCE(all_stats.accessed_links,0),
				       COALESCE(all_stats.bot_only_links,0),COALESCE(period.redirects,0),COALESCE(period.redirects,0)
				  FROM keys LEFT JOIN current_config config USING (field_name,field_value)
				  LEFT JOIN all_stats USING (field_name,field_value) LEFT JOIN period_stats period USING (field_name,field_value)
				 ORDER BY 6 DESC,keys.field_name,keys.field_value LIMIT 500
				""";
		return jdbc.query(sql, (rs, row) -> new UtmRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
				rs.getLong(5), rs.getLong(6), rs.getLong(7)), campaignId, timestamp(p.start), timestamp(p.end));
	}

	private List<CampaignRow> campaignRows(Long projectId, Period p) {
		String sql = """
				SELECT c.id,c.name,COUNT(DISTINCT l.id),COUNT(e.id),COUNT(e.id) FILTER (WHERE e.outcome='REDIRECTED')
				  FROM campaigns c LEFT JOIN links l ON l.campaign_id=c.id AND NOT l.is_deleted
				  LEFT JOIN link_access_events e ON e.link_id=l.id AND e.accessed_at>=? AND e.accessed_at<?
				 WHERE c.project_id=? GROUP BY c.id ORDER BY 4 DESC,c.id DESC
				""";
		return jdbc.query(sql, (rs, row) -> new CampaignRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)),
				timestamp(p.start), timestamp(p.end), projectId);
	}

	private Period period(LocalDate from, LocalDate to) {
		LocalDate endDate = to == null ? LocalDate.now(ZONE) : to;
		LocalDate startDate = from == null ? endDate.minusDays(29) : from;
		if (startDate.isAfter(endDate) || Duration.between(startDate.atStartOfDay(ZONE), endDate.plusDays(1).atStartOfDay(ZONE)).toDays() > 3660)
			throw new IllegalArgumentException("통계 기간은 10년 이내이며 시작일이 종료일보다 늦을 수 없습니다.");
		Instant start = startDate.atStartOfDay(ZONE).toInstant();
		Instant end = endDate.plusDays(1).atStartOfDay(ZONE).toInstant();
		Duration length = Duration.between(start, end);
		return new Period(startDate, endDate, start, end, start.minus(length));
	}

	private Object[] args(Object first, Object second, Object[] rest) {
		Object[] values = new Object[rest.length + 2]; values[0] = sqlValue(first); values[1] = sqlValue(second);
		System.arraycopy(rest, 0, values, 2, rest.length); return values;
	}
	private Object sqlValue(Object value) { return value instanceof Instant instant ? timestamp(instant) : value; }
	private java.sql.Timestamp timestamp(Instant value) { return java.sql.Timestamp.from(value); }
	private Breakdown item(String name, long count, long total) { return new Breakdown(name, count, total == 0 ? 0 : count * 100.0 / total); }
	private String status(long events, long humans) { return humans > 0 ? "CHECKED" : events > 0 ? "BOT_ONLY" : "UNCHECKED"; }
	private Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
	private String referrerDomain(String value) {
		if (value == null || value.isBlank()) return "직접 유입";
		try { String host = URI.create(value).getHost(); return host == null ? "기타" : host.toLowerCase(Locale.ROOT); }
		catch (IllegalArgumentException exception) { return "기타"; }
	}

	public enum Bucket { DAY, MONTH, YEAR }
	private record Period(LocalDate from, LocalDate to, Instant start, Instant end, Instant previousStart) { }
}

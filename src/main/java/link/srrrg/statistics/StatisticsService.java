package link.srrrg.statistics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import link.srrrg.statistics.StatisticsQueryRepository.Dimension;
import link.srrrg.statistics.StatisticsResponse.Breakdown;
import link.srrrg.statistics.StatisticsResponse.Bucket;
import link.srrrg.statistics.StatisticsResponse.CampaignRow;
import link.srrrg.statistics.StatisticsResponse.LifetimeLinkSummary;
import link.srrrg.statistics.StatisticsResponse.LinkRow;
import link.srrrg.statistics.StatisticsResponse.OutcomeBreakdown;
import link.srrrg.statistics.StatisticsResponse.PageResult;
import link.srrrg.statistics.StatisticsResponse.RecentActivity;
import link.srrrg.statistics.StatisticsResponse.Scope;
import link.srrrg.statistics.StatisticsResponse.Summary;
import link.srrrg.statistics.StatisticsResponse.TrendPoint;
import link.srrrg.statistics.StatisticsResponse.UtmRow;

@Service
public class StatisticsService {
	static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private final StatisticsQueryRepository queries;
	private final Clock clock;

	@Autowired
	public StatisticsService(StatisticsQueryRepository queries) {
		this(queries, Clock.system(ZONE));
	}

	StatisticsService(StatisticsQueryRepository queries, Clock clock) {
		this.queries = queries;
		this.clock = clock;
	}

	public StatisticsResponse link(long linkId, String name, LocalDate from, LocalDate to, Bucket bucket,
			int offset, int limit) {
		return report(new StatisticsQueryScope(Scope.LINK, linkId), name, from, to, bucket, offset, limit);
	}

	public StatisticsResponse campaign(long campaignId, String name, LocalDate from, LocalDate to, Bucket bucket,
			int offset, int limit) {
		return report(new StatisticsQueryScope(Scope.CAMPAIGN, campaignId), name, from, to, bucket, offset, limit);
	}

	public StatisticsResponse project(long projectId, String name, LocalDate from, LocalDate to, Bucket bucket,
			int offset, int limit) {
		return report(new StatisticsQueryScope(Scope.PROJECT, projectId), name, from, to, bucket, offset, limit);
	}

	private StatisticsResponse report(StatisticsQueryScope scope, String name, LocalDate requestedFrom,
			LocalDate requestedTo, Bucket bucket, int offset, int limit) {
		validatePage(offset, limit);
		StatisticsPeriod period = period(requestedFrom, requestedTo);
		validateBucketRange(period, bucket);
		var totals = queries.totals(scope, period);
		long[] lifetime = queries.lifetimeLinkSummary(scope);
		Summary summary = new Summary(totals.current(), totals.previous(),
				new LifetimeLinkSummary(lifetime[0], lifetime[1], lifetime[2], lifetime[3], lifetime[4], lifetime[5]));

		PageResult<LinkRow> links = PageResult.empty();
		PageResult<UtmRow> utm = PageResult.empty();
		PageResult<CampaignRow> campaigns = PageResult.empty();
		if (scope.type() == Scope.CAMPAIGN) {
			links = queries.campaignLinks(scope.id(), period, offset, limit);
			utm = queries.campaignUtm(scope.id(), period, offset, limit);
		} else if (scope.type() == Scope.PROJECT) {
			campaigns = queries.projectCampaigns(scope.id(), period, offset, limit);
		}

		List<OutcomeBreakdown> outcomes = queries.outcomes(scope, period);
		List<Breakdown> referrers = queries.breakdown(scope, period, Dimension.REFERRER);
		List<Breakdown> devices = queries.breakdown(scope, period, Dimension.DEVICE);
		List<Breakdown> browsers = queries.breakdown(scope, period, Dimension.BROWSER);
		List<Breakdown> operatingSystems = queries.breakdown(scope, period, Dimension.OPERATING_SYSTEM);
		List<RecentActivity> recent = queries.recent(scope, period);

		return new StatisticsResponse(scope.type(), name, period.from(), period.to(), bucket, summary,
				completeTrend(queries.trend(scope, period, bucket), period, bucket), outcomes,
				referrers, devices, browsers, operatingSystems, recent, links, utm, campaigns);
	}

	private StatisticsPeriod period(LocalDate from, LocalDate to) {
		LocalDate endDate = to == null ? LocalDate.now(clock) : to;
		LocalDate startDate = from == null ? endDate.minusDays(29) : from;
		if (startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
		}
		Instant start = startDate.atStartOfDay(ZONE).toInstant();
		Instant end = endDate.plusDays(1).atStartOfDay(ZONE).toInstant();
		Duration length = Duration.between(start, end);
		if (length.toDays() > 3660) {
			throw new IllegalArgumentException("통계 기간은 10년 이내여야 합니다.");
		}
		return new StatisticsPeriod(startDate, endDate, start, end, start.minus(length));
	}

	private void validateBucketRange(StatisticsPeriod period, Bucket bucket) {
		long points = switch (bucket) {
			case DAY -> ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
			case MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(period.from()), YearMonth.from(period.to())) + 1;
			case YEAR -> period.to().getYear() - period.from().getYear() + 1L;
		};
		long maximum = switch (bucket) { case DAY -> 366; case MONTH -> 120; case YEAR -> 10; };
		if (points > maximum) {
			throw new IllegalArgumentException("선택한 기간에는 더 큰 집계 단위를 사용하세요.");
		}
	}

	private void validatePage(int offset, int limit) {
		if (offset < 0 || offset > 100_000) throw new IllegalArgumentException("offset은 0~100000 사이여야 합니다.");
		if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit은 1~100 사이여야 합니다.");
	}

	private List<TrendPoint> completeTrend(List<StatisticsQueryRepository.RawTrendPoint> found,
			StatisticsPeriod period, Bucket bucket) {
		Map<LocalDate, StatisticsQueryRepository.RawTrendPoint> byDate = found.stream()
				.collect(Collectors.toMap(StatisticsQueryRepository.RawTrendPoint::date, Function.identity()));
		List<TrendPoint> complete = new ArrayList<>();
		LocalDate cursor = switch (bucket) {
			case DAY -> period.from();
			case MONTH -> period.from().withDayOfMonth(1);
			case YEAR -> period.from().withDayOfYear(1);
		};
		while (!cursor.isAfter(period.to())) {
			var point = byDate.get(cursor);
			complete.add(point == null ? new TrendPoint(cursor, 0, 0, 0, 0, 0, 0) : point.toResponse());
			cursor = switch (bucket) { case DAY -> cursor.plusDays(1); case MONTH -> cursor.plusMonths(1); case YEAR -> cursor.plusYears(1); };
		}
		return List.copyOf(complete);
	}
}

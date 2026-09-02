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

/**
 * 통계 리포트 하나를 구성한다. 기간을 확정하고, 여러 집계 쿼리를 모아, 화면이 바로 그릴 수 있는 형태로 맞춘다.
 *
 * <p>인가는 하지 않는다. 어떤 링크·캠페인·프로젝트의 통계를 볼 수 있는지는 컨트롤러가 이미 확인했다는
 * 전제이며, 여기에는 식별자만 넘어온다. 확인 없이 부르면 남의 통계가 그대로 나간다.</p>
 *
 * <p>집계 기준 시간대를 서울로 고정한다. UTC로 자르면 하루 경계가 사용자가 인식하는 날짜와 어긋나
 * 같은 데이터가 다른 날에 잡힌다. 이 값이 바뀌면 과거 리포트의 일별 수치도 함께 달라진다.</p>
 *
 * <p>{@code Clock}을 주입받는 생성자를 따로 둔 것은 테스트에서 오늘 날짜를 고정하기 위해서다.</p>
 */
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

	/**
	 * 범위와 기간을 받아 리포트 전체를 만든다. 세 진입점이 모두 이 메서드로 모인다.
	 *
	 * <p>목록 집계는 범위에 따라 다른 것만 채운다. 캠페인이면 소속 링크와 UTM별 성과를, 프로젝트면
	 * 캠페인별 성과를 낸다. 링크 하나에는 더 쪼갤 축이 없어 셋 다 비운다.</p>
	 *
	 * <p>여러 쿼리를 순차로 실행하므로 각 결과의 기준 시각이 미세하게 다를 수 있다.
	 * 기간 경계는 미리 확정한 값을 모든 쿼리에 넘겨 그 차이가 집계 구간까지 흔들지 않게 한다.</p>
	 */
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

	/**
	 * 요청 기간을 조회에 쓸 시각 범위로 바꾼다. 값을 주지 않으면 오늘까지 최근 30일이 기본이다.
	 *
	 * <p>종료일은 그날 자정이 아니라 다음 날 0시를 끝으로 잡는다. 종료일 당일의 접근을 포함하기 위해서다.
	 * 시작은 포함하고 끝은 제외하는 범위라 구간이 겹치거나 빠지지 않는다.</p>
	 *
	 * <p>직전 기간의 시작을 함께 계산해 두는 것은 증감 비교 때문이다. 같은 길이만큼 앞으로 밀어
	 * 30일을 보면 그 앞 30일과 비교된다.</p>
	 *
	 * @throws IllegalArgumentException 시작이 종료보다 늦거나 기간이 10년을 넘는 경우.
	 *                                  긴 기간은 집계 쿼리 비용이 그대로 커지므로 상한을 둔다
	 */
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

	/**
	 * 기간과 집계 단위의 조합이 만들 점 개수를 미리 세어 상한을 넘으면 거부한다.
	 *
	 * <p>10년치를 일 단위로 요청하면 삼천 개가 넘는 점이 만들어져 응답도 화면도 감당하지 못한다.
	 * 쿼리를 실행하고 나서 자르는 대신 실행 전에 막고, 더 큰 단위를 쓰라고 알려준다.</p>
	 */
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

	/**
	 * 집계 결과에 없는 구간을 0으로 채워 빠짐없는 시계열을 만든다.
	 *
	 * <p>집계 쿼리는 접근이 있었던 구간만 돌려주므로, 그대로 그리면 데이터가 없는 날이 건너뛰어져
	 * 그래프의 가로축 간격이 왜곡된다. 시작점을 단위 경계로 내리는 것도 같은 이유다.
	 * 월 단위인데 15일부터 시작하면 첫 점이 그달의 일부만 담은 것처럼 보인다.</p>
	 */
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

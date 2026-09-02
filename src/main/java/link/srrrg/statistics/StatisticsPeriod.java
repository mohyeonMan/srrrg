package link.srrrg.statistics;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 확정된 통계 기간. 화면에 표시할 날짜({@code from}, {@code to})와 쿼리에 넘길 시각 경계를 함께 담는다.
 *
 * <p>{@code start}는 포함, {@code end}는 제외다. {@code end}가 종료일 다음 날 0시라
 * 종료일 당일의 접근이 모두 들어간다. {@code previousStart}는 같은 길이의 직전 기간 시작으로,
 * 증감 비교에만 쓰인다.</p>
 *
 * <p>모든 쿼리가 이 한 값을 공유해야 결과들의 집계 구간이 어긋나지 않는다.</p>
 */
record StatisticsPeriod(LocalDate from, LocalDate to, Instant start, Instant end, Instant previousStart) { }

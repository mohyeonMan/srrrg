package link.srrrg.statistics.controller;

import link.srrrg.statistics.dto.StatisticsResponse;
import link.srrrg.statistics.service.StatisticsService;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import link.srrrg.statistics.dto.StatisticsResponse.Bucket;

/**
 * secret key로 관리하는 익명 링크의 통계 HTTP 계약을 담당한다.
 * 소유 확인과 링크 조회는 {@link StatisticsService}가 수행하며 이 클래스는 요청 값을 전달한다.
 */
@RestController
public class AnonymousStatisticsController {
	private static final String SECRET_KEY = "X-Srrrg-Secret-Key";
	private final StatisticsService statisticsService;

	public AnonymousStatisticsController(StatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}

	@GetMapping("/api/links/{code}/statistics")
	public StatisticsResponse link(@PathVariable String code, @RequestHeader(SECRET_KEY) String secret,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "DAY") Bucket bucket,
			@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) {
		return statisticsService.anonymousLink(code, secret, from, to, bucket, offset, limit);
	}
}

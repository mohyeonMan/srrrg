package link.srrrg.link.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import link.srrrg.common.metrics.SrrrgMetrics;

class FixedSafeUrlRiskCheckerTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	@Test
	void returnsSafeAssessmentWithConfiguredCacheDuration() {
		Duration cacheDuration = Duration.ofMinutes(3);
		FixedSafeUrlRiskChecker checker = checker(Duration.ZERO, cacheDuration);

		Instant before = Instant.now();
		UrlRiskAssessment assessment = checker.check("https://example.com");

		assertThat(assessment.verdict()).isEqualTo(RiskVerdict.SAFE);
		assertThat(assessment.verifiedAt()).isBetween(before, Instant.now());
		assertThat(assessment.expiresAt()).isEqualTo(assessment.verifiedAt().plus(cacheDuration));
		assertThat(registry.get("srrrg.url.risk.check")
				.tag("provider", "fixed_safe")
				.tag("outcome", "safe")
				.timer()
				.count()).isEqualTo(1);
	}

	@Test
	void waitsForConfiguredDelayBeforeReturning() {
		FixedSafeUrlRiskChecker checker = checker(Duration.ofMillis(20), Duration.ofMinutes(5));

		long startedAt = System.nanoTime();
		checker.check("https://example.com");

		assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThanOrEqualTo(Duration.ofMillis(15));
	}

	private FixedSafeUrlRiskChecker checker(Duration delay, Duration cacheDuration) {
		var fixedSafe = new UrlRiskCheckerProperties.FixedSafe(delay, cacheDuration);
		var properties = new UrlRiskCheckerProperties(
				UrlRiskCheckerProperties.Provider.FIXED_SAFE,
				fixedSafe
		);
		return new FixedSafeUrlRiskChecker(properties, new SrrrgMetrics(registry));
	}
}

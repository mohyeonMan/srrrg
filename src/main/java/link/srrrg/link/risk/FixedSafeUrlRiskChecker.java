package link.srrrg.link.risk;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import link.srrrg.common.metrics.SrrrgMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(
		prefix = "srrrg.url-risk",
		name = "provider",
		havingValue = "fixed-safe"
)
@RequiredArgsConstructor
@Slf4j
public class FixedSafeUrlRiskChecker implements UrlRiskChecker {

	private final UrlRiskCheckerProperties properties;
	private final SrrrgMetrics metrics;

	@PostConstruct
	void logConfiguration() {
		log.warn("Fixed-safe URL risk checker enabled: delay={}, cacheDuration={}",
				settings().delay(), settings().cacheDuration());
	}

	@Override
	public UrlRiskAssessment check(String url) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			UrlRiskCheckerProperties.FixedSafe settings = settings();
			if (!waitFor(settings.delay())) {
				return UrlRiskAssessment.unknown(Instant.now());
			}

			Instant verifiedAt = Instant.now();
			outcome = "safe";
			return new UrlRiskAssessment(
					RiskVerdict.SAFE,
					verifiedAt,
					verifiedAt.plus(settings.cacheDuration())
			);
		} finally {
			metrics.recordUrlRiskCheck(sample, "fixed_safe", outcome);
		}
	}

	private UrlRiskCheckerProperties.FixedSafe settings() {
		return properties.fixedSafe();
	}

	private boolean waitFor(Duration delay) {
		if (delay.isZero()) {
			return true;
		}
		try {
			TimeUnit.NANOSECONDS.sleep(delay.toNanos());
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}

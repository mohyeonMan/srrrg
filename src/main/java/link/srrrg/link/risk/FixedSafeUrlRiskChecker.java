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

/**
 * 외부 호출 없이 항상 안전으로 답하는 개발·테스트용 구현. 설정으로 명시해야만 활성화된다.
 *
 * <p>이 구현이 켜져 있으면 위협 URL도 그대로 통과하므로, 기동 시 경고 로그를 남겨
 * 운영에 잘못 배포된 상태를 알아차릴 수 있게 한다. 지연 설정은 외부 검사가 느릴 때의
 * 동작을 재현하기 위한 것이다.</p>
 */
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

	/**
	 * 설정된 지연만큼 기다린다. 중단되면 인터럽트 상태를 되살리고 거짓을 돌려주며,
	 * 호출자는 이를 판정 불가로 처리한다. 인터럽트를 삼키면 종료 중인 스레드가 계속 돈다.
	 */
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

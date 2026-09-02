package link.srrrg.link.risk;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("srrrg.url-risk")
/**
 * URL 위험 검사 공급자 설정. 값을 주지 않으면 실제 검사를 하는 Google 공급자가 기본이다.
 * 기본값을 개발용으로 두면 설정을 빠뜨린 환경이 검사 없이 동작하게 되므로, 안전한 쪽을 기본으로 삼는다.
 */
public record UrlRiskCheckerProperties(
		Provider provider,
		FixedSafe fixedSafe
) {

	public UrlRiskCheckerProperties {
		provider = provider == null ? Provider.GOOGLE : provider;
		fixedSafe = fixedSafe == null
				? new FixedSafe(Duration.ZERO, Duration.ofMinutes(5))
				: fixedSafe;
	}

	public enum Provider {
		GOOGLE,
		FIXED_SAFE
	}

	public record FixedSafe(
			Duration delay,
			Duration cacheDuration
	) {

		/**
		 * 개발용 설정이라도 값이 어긋나면 기동 시점에 실패시킨다. 음수 지연이나 0 캐시 기간은
		 * 실행 중에 예외나 무한 재검사로 드러나기 때문이다.
		 */
		public FixedSafe {
			delay = delay == null ? Duration.ZERO : delay;
			cacheDuration = cacheDuration == null ? Duration.ofMinutes(5) : cacheDuration;
			if (delay.isNegative()) {
				throw new IllegalArgumentException("Fixed-safe delay must not be negative");
			}
			if (cacheDuration.isZero() || cacheDuration.isNegative()) {
				throw new IllegalArgumentException("Fixed-safe cache duration must be positive");
			}
		}
	}
}

package link.srrrg.link.risk;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("srrrg.url-risk")
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

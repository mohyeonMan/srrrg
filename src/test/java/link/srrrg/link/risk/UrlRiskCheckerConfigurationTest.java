package link.srrrg.link.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.link.risk.google.GoogleSafeBrowsingClient;
import link.srrrg.link.risk.google.GoogleSafeBrowsingProperties;

class UrlRiskCheckerConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(
					UrlRiskCheckerConfig.class,
					FixedSafeUrlRiskChecker.class,
					GoogleSafeBrowsingClient.class
			)
			.withBean(RestClient.class, () -> mock(RestClient.class))
			.withBean(SrrrgMetrics.class, () -> new SrrrgMetrics(new SimpleMeterRegistry()))
			.withBean(
					GoogleSafeBrowsingProperties.class,
					() -> new GoogleSafeBrowsingProperties(
							"test-key",
							"https://safe.example/v5/urls:search",
							Duration.ofSeconds(1),
							Duration.ofSeconds(1)
					)
			);

	@Test
	void selectsGoogleCheckerByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(UrlRiskChecker.class);
			assertThat(context.getBean(UrlRiskChecker.class)).isInstanceOf(GoogleSafeBrowsingClient.class);
		});
	}

	@Test
	void selectsFixedSafeCheckerWhenConfigured() {
		contextRunner
				.withPropertyValues(
						"srrrg.url-risk.provider=fixed-safe",
						"srrrg.url-risk.fixed-safe.delay=25ms",
						"srrrg.url-risk.fixed-safe.cache-duration=2m"
				)
				.run(context -> {
					assertThat(context).hasSingleBean(UrlRiskChecker.class);
					assertThat(context.getBean(UrlRiskChecker.class)).isInstanceOf(FixedSafeUrlRiskChecker.class);
					UrlRiskCheckerProperties properties = context.getBean(UrlRiskCheckerProperties.class);
					assertThat(properties.provider()).isEqualTo(UrlRiskCheckerProperties.Provider.FIXED_SAFE);
					assertThat(properties.fixedSafe().delay()).isEqualTo(Duration.ofMillis(25));
					assertThat(properties.fixedSafe().cacheDuration()).isEqualTo(Duration.ofMinutes(2));
				});
	}
}

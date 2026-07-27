package link.srrrg.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

class SrrrgMetricsConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer())
			.withConfiguration(AutoConfigurations.of(
					MetricsAutoConfiguration.class,
					PrometheusMetricsExportAutoConfiguration.class
			))
			.withBean(SrrrgMetrics.class);

	@Test
	void publishesAggregatableHistogramAndSloBucketsWithoutClientSideQuantiles() {
		contextRunner.run(context -> {
			SrrrgMetrics metrics = context.getBean(SrrrgMetrics.class);
			metrics.recordRedirect(metrics.startTimer(), "redirected");
			metrics.recordRedirectWrite(metrics.startTimer(), "click", "success");
			metrics.recordLinkCreate(metrics.startTimer(), "created");
			metrics.recordUrlRiskCheck(metrics.startTimer(), "fixed_safe", "safe");
			metrics.recordUrlRiskCache("hit");
			metrics.recordLinkCodeGeneration("collision");

			String scrape = context.getBean(PrometheusMeterRegistry.class).scrape();
			MetricsProperties.Distribution distribution = context.getBean(MetricsProperties.class)
					.getDistribution();

			assertThat(scrape)
					.contains("srrrg_redirect_seconds_bucket")
					.contains("srrrg_redirect_write_seconds_bucket")
					.contains("srrrg_link_create_seconds_bucket")
					.contains("srrrg_url_risk_check_seconds_bucket")
					.contains("srrrg_url_risk_cache_total")
					.contains("srrrg_link_code_generation_total")
					.contains("le=\"0.1\"")
					.contains("le=\"0.25\"")
					.contains("srrrg_redirect_seconds_count")
					.contains("srrrg_redirect_seconds_sum")
					.doesNotContain("quantile=");
			assertThat(distribution.getPercentilesHistogram())
					.containsEntry("http.server.requests", true)
					.containsEntry("srrrg.redirect", true)
					.containsEntry("srrrg.redirect.write", true)
					.containsEntry("srrrg.link.create", true)
					.containsEntry("srrrg.url.risk.check", true);
			assertThat(distribution.getPercentiles()).isEmpty();
		});
	}
}

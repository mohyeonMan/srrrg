package link.srrrg.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataSourceConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer())
			.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class));

	@Test
	void bindsExplicitHikariPoolAndTimeoutSettings() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(DataSource.class);
			HikariDataSource dataSource = context.getBean(HikariDataSource.class);

			assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
			assertThat(dataSource.getMinimumIdle()).isEqualTo(10);
			assertThat(dataSource.getConnectionTimeout()).isEqualTo(2_000);
			assertThat(dataSource.getValidationTimeout()).isEqualTo(1_000);
		});
	}

	@Test
	void allowsHikariSettingsToBeOverridden() {
		contextRunner
				.withPropertyValues(
						"SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=7",
						"SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=4",
						"SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=3000",
						"SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT=1500"
				)
				.run(context -> {
					HikariDataSource dataSource = context.getBean(HikariDataSource.class);

					assertThat(dataSource.getMaximumPoolSize()).isEqualTo(7);
					assertThat(dataSource.getMinimumIdle()).isEqualTo(4);
					assertThat(dataSource.getConnectionTimeout()).isEqualTo(3_000);
					assertThat(dataSource.getValidationTimeout()).isEqualTo(1_500);
				});
	}
}

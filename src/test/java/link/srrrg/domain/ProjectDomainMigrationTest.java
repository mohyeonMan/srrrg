package link.srrrg.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ProjectDomainMigrationTest {
	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@Test
	void supportsOptionalProjectSubdomainsAndAddressScopedCodes() {
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.placeholders(Map.of("platformBaseUrl", "https://dev.srrrg.link"))
				.load()
				.migrate();
		JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

		Long first = jdbc.queryForObject("INSERT INTO projects (name) VALUES ('First') RETURNING id", Long.class);
		Long second = jdbc.queryForObject("INSERT INTO projects (name) VALUES ('Second') RETURNING id", Long.class);
		assertThat(jdbc.queryForObject("SELECT subdomain IS NULL FROM projects WHERE id = ?", Boolean.class, first)).isTrue();

		jdbc.update("UPDATE projects SET subdomain = 'team', subdomain_enabled = TRUE WHERE id = ?", first);
		assertThatThrownBy(() -> jdbc.update("UPDATE projects SET subdomain = 'team' WHERE id = ?", second))
				.isInstanceOf(DataIntegrityViolationException.class);

		jdbc.update("INSERT INTO links (code, original_url, project_id) VALUES ('Base01', 'https://one.example', ?)", first);
		assertThatThrownBy(() -> jdbc.update("INSERT INTO links (code, original_url, project_id) VALUES ('Base01', 'https://two.example', ?)", second))
				.isInstanceOf(DataIntegrityViolationException.class);

		jdbc.update("INSERT INTO links (code, original_url, project_id, subdomain) VALUES ('Same01', 'https://one.example', ?, 'team')", first);
		jdbc.update("INSERT INTO links (code, original_url, project_id, subdomain) VALUES ('Same01', 'https://two.example', ?, 'brand')", second);
		assertThatThrownBy(() -> jdbc.update("INSERT INTO links (code, original_url, project_id, subdomain) VALUES ('Same01', 'https://three.example', ?, 'team')", second))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}

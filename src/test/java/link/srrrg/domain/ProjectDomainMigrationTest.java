package link.srrrg.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
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
	void backfillsExistingProjectsAndProjectLinksWithoutChangingAnonymousLinks() {
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.placeholders(Map.of("platformBaseUrl", "https://dev.srrrg.link"))
				.target(MigrationVersion.fromVersion("16"))
				.load()
				.migrate();
		JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
		Long projectId = jdbc.queryForObject(
				"INSERT INTO projects (name, slug) VALUES ('Legacy', 'legacy') RETURNING id", Long.class);
		jdbc.update("INSERT INTO links (code, original_url, secret_key_hash) VALUES ('Anon01', 'https://anonymous.example', 'hash')");
		jdbc.update("INSERT INTO links (code, original_url, project_id) VALUES ('Proj01', 'https://project.example', ?)", projectId);

		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.placeholders(Map.of("platformBaseUrl", "https://dev.srrrg.link"))
				.load()
				.migrate();

		Long domainId = jdbc.queryForObject(
				"SELECT id FROM project_domains WHERE project_id = ?", Long.class, projectId);
		assertThat(jdbc.queryForObject(
				"SELECT hostname FROM project_domains WHERE id = ?", String.class, domainId))
				.isEqualTo("legacy.dev.srrrg.link");
		assertThat(jdbc.queryForObject(
				"SELECT domain_id FROM links WHERE code = 'Proj01'", Long.class)).isEqualTo(domainId);
		assertThat(jdbc.queryForObject(
				"SELECT domain_id IS NULL FROM links WHERE code = 'Anon01'", Boolean.class)).isTrue();
	}
}

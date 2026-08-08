package link.srrrg.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import link.srrrg.project.Project;

class ProjectDomainServiceTest {
	private final ProjectDomainRepository repository = mock(ProjectDomainRepository.class);

	@Test
	void createsPlatformHostnameFromConfiguredBaseUrl() {
		Project project = mock(Project.class);
		when(project.getSlug()).thenReturn("acme");
		when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));
		ProjectDomainService service = new ProjectDomainService(repository, "https://dev.srrrg.link");

		ProjectDomain domain = service.create(project);

		assertThat(domain.getHostname()).isEqualTo("acme.dev.srrrg.link");
		assertThat(domain.getProject()).isSameAs(project);
	}

	@Test
	void resolvesOnlyBaseAndRegisteredHostHeaders() {
		ProjectDomain domain = mock(ProjectDomain.class);
		when(domain.getId()).thenReturn(7L);
		when(repository.findByHostname("acme.srrrg.link")).thenReturn(Optional.of(domain));
		ProjectDomainService service = new ProjectDomainService(repository, "https://srrrg.link");

		assertThat(service.resolve("SRRRG.LINK:443")).contains(new ProjectDomainService.HostRoute(null));
		assertThat(service.resolve("acme.srrrg.link")).contains(new ProjectDomainService.HostRoute(7L));
		assertThat(service.resolve("unknown.srrrg.link")).isEmpty();
		assertThat(service.resolve("acme.srrrg.link, attacker.example")).isEmpty();
		assertThat(service.isReservedSlug("admin")).isTrue();
	}

	@Test
	void changesPlatformHostnameWithoutReplacingDomain() {
		ProjectDomain domain = mock(ProjectDomain.class);
		when(repository.findByIdAndProjectId(3L, 7L)).thenReturn(Optional.of(domain));
		when(repository.saveAndFlush(domain)).thenReturn(domain);
		ProjectDomainService service = new ProjectDomainService(repository, "https://srrrg.link");

		assertThat(service.change(7L, 3L, "renamed")).isSameAs(domain);
		verify(domain).changeHostname("renamed.srrrg.link");
	}
}

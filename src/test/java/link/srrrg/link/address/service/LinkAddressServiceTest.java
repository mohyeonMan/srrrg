package link.srrrg.link.address.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkAddressServiceTest {
	@Test
	void buildsHostnamesAndOriginsFromConfiguredBaseUrl() {
		LinkAddressService service = new LinkAddressService("https://dev.srrrg.link:8443");

		assertThat(service.hostname(null)).isEqualTo("dev.srrrg.link");
		assertThat(service.hostname("acme")).isEqualTo("acme.dev.srrrg.link");
		assertThat(service.origin("acme")).isEqualTo("https://acme.dev.srrrg.link:8443");
	}

	@Test
	void resolvesOnlyBaseAndSinglePlatformSubdomain() {
		LinkAddressService service = new LinkAddressService("https://srrrg.link");

		assertThat(service.resolve("SRRRG.LINK:443")).contains(new LinkAddressService.HostRoute(null));
		assertThat(service.resolve("acme.srrrg.link")).contains(new LinkAddressService.HostRoute("acme"));
		assertThat(service.resolve("nested.acme.srrrg.link")).isEmpty();
		assertThat(service.resolve("unknown.example")).isEmpty();
		assertThat(service.resolve("acme.srrrg.link, attacker.example")).isEmpty();
	}
}

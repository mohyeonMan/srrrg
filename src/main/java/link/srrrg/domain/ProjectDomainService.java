package link.srrrg.domain;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import link.srrrg.project.Project;

@Service
public class ProjectDomainService {
	private static final Set<String> RESERVED_SLUGS = Set.of(
			"actuator", "admin", "api", "app", "auth", "cdn", "cname", "dev", "docs", "help",
			"login", "mail", "manage", "oauth", "oauth2", "openapi", "static", "status", "support", "www");
	private final ProjectDomainRepository domains;
	private final String baseHostname;

	public ProjectDomainService(ProjectDomainRepository domains, @Value("${srrrg.base-url}") String baseUrl) {
		this.domains = domains;
		URI uri = URI.create(baseUrl);
		if (uri.getHost() == null) throw new IllegalArgumentException("srrrg.base-url에 유효한 host가 필요합니다.");
		this.baseHostname = normalizeHostname(uri.getHost());
	}

	public ProjectDomain create(Project project) {
		return domains.save(ProjectDomain.create(project, project.getSlug() + "." + baseHostname));
	}

	public boolean isReservedSlug(String slug) {
		return RESERVED_SLUGS.contains(slug);
	}

	public ProjectDomain get(Long projectId) {
		return domains.findByProjectId(projectId)
				.orElseThrow(() -> new IllegalStateException("프로젝트 도메인을 찾을 수 없습니다."));
	}

	public Optional<HostRoute> resolve(String hostHeader) {
		String hostname = hostname(hostHeader);
		if (hostname == null) return Optional.empty();
		if (baseHostname.equals(hostname)) return Optional.of(new HostRoute(null));
		return domains.findByHostname(hostname).map(domain -> new HostRoute(domain.getId()));
	}

	private String hostname(String hostHeader) {
		if (hostHeader == null || hostHeader.isBlank()) return null;
		try {
			URI uri = URI.create("http://" + hostHeader.trim());
			if (uri.getHost() == null || uri.getUserInfo() != null || !uri.getPath().isEmpty()
					|| uri.getQuery() != null || uri.getFragment() != null) return null;
			return normalizeHostname(uri.getHost());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String normalizeHostname(String hostname) {
		String normalized = hostname.endsWith(".") ? hostname.substring(0, hostname.length() - 1) : hostname;
		return IDN.toASCII(normalized).toLowerCase(Locale.ROOT);
	}

	public record HostRoute(Long domainId) {
		public boolean isBaseDomain() { return domainId == null; }
	}
}

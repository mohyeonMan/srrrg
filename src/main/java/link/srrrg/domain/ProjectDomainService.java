package link.srrrg.domain;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProjectDomainService {
	private static final Set<String> RESERVED_SLUGS = Set.of(
			"actuator", "admin", "api", "app", "auth", "cdn", "cname", "dev", "docs", "help",
			"login", "mail", "manage", "oauth", "oauth2", "openapi", "static", "status", "support", "www");
	private final String baseHostname;
	private final String scheme;
	private final int port;

	public ProjectDomainService(@Value("${srrrg.base-url}") String baseUrl) {
		URI uri = URI.create(baseUrl);
		if (uri.getHost() == null) throw new IllegalArgumentException("srrrg.base-url에 유효한 host가 필요합니다.");
		this.baseHostname = normalizeHostname(uri.getHost());
		this.scheme = uri.getScheme();
		this.port = uri.getPort();
	}

	public boolean isReservedSubdomain(String subdomain) {
		return RESERVED_SLUGS.contains(subdomain);
	}

	public String hostname(String subdomain) {
		return subdomain == null ? baseHostname : subdomain + "." + baseHostname;
	}

	public String origin(String subdomain) {
		return scheme + "://" + hostname(subdomain) + (port < 0 ? "" : ":" + port);
	}

	public Optional<HostRoute> resolve(String hostHeader) {
		String hostname = parseHostname(hostHeader);
		if (hostname == null) return Optional.empty();
		if (baseHostname.equals(hostname)) return Optional.of(new HostRoute(null));
		String suffix = "." + baseHostname;
		String subdomain = hostname.endsWith(suffix) ? hostname.substring(0, hostname.length() - suffix.length()) : "";
		return subdomain.isEmpty() || subdomain.contains(".") ? Optional.empty() : Optional.of(new HostRoute(subdomain));
	}

	private String parseHostname(String hostHeader) {
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

	public record HostRoute(String subdomain) {
		public boolean isBaseDomain() { return subdomain == null; }
	}
}

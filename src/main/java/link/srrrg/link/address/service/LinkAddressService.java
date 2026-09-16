package link.srrrg.link.address.service;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 링크 주소 생성과 요청 {@code Host} 해석에 같은 베이스 도메인 규칙을 적용한다.
 * 클라이언트가 보낸 호스트는 형식을 검증한 뒤에만 링크 코드 공간을 고르는 데 사용한다.
 */
@Service
public class LinkAddressService {
	private final String baseHostname;
	private final String scheme;
	private final int port;

	public LinkAddressService(@Value("${srrrg.base-url}") String baseUrl) {
		URI uri = URI.create(baseUrl);
		if (uri.getHost() == null) throw new IllegalArgumentException("srrrg.base-url에 유효한 host가 필요합니다.");
		this.baseHostname = normalizeHostname(uri.getHost());
		this.scheme = uri.getScheme();
		this.port = uri.getPort();
	}

	public String hostname(String subdomain) {
		return subdomain == null ? baseHostname : subdomain + "." + baseHostname;
	}

	public String origin(String subdomain) {
		return scheme + "://" + hostname(subdomain) + (port < 0 ? "" : ":" + port);
	}

	/** 우리 베이스 도메인이나 한 단계 프로젝트 서브도메인만 코드 공간으로 인정한다. */
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

	/** {@code subdomain == null}이면 플랫폼 베이스 도메인의 코드 공간이다. */
	public record HostRoute(String subdomain) {
		public boolean isBaseDomain() {
			return subdomain == null;
		}
	}
}

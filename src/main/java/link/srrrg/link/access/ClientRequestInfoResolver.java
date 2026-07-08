package link.srrrg.link.access;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientRequestInfoResolver {

	private static final int MAX_HEADER_LENGTH = 2048;

	private final boolean trustForwardedHeaders;

	public ClientRequestInfoResolver(
			@Value("${srrrg.trust-forwarded-headers:false}") boolean trustForwardedHeaders
	) {
		this.trustForwardedHeaders = trustForwardedHeaders;
	}

	public ClientRequestInfo resolve(HttpServletRequest request) {
		return new ClientRequestInfo(
				resolveIpAddress(request),
				truncate(request.getHeader("Referer")),
				truncate(request.getHeader("User-Agent"))
		);
	}

	private String resolveIpAddress(HttpServletRequest request) {
		if (trustForwardedHeaders) {
			String forwardedFor = request.getHeader("X-Forwarded-For");
			if (forwardedFor != null && !forwardedFor.isBlank()) {
				String forwardedIp = normalizeIp(forwardedFor.split(",", 2)[0]);
				if (forwardedIp != null) {
					return forwardedIp;
				}
			}
		}
		return normalizeIp(request.getRemoteAddr());
	}

	private String normalizeIp(String value) {
		if (value == null) {
			return null;
		}

		String candidate = value.trim();
		if (candidate.startsWith("[") && candidate.endsWith("]")) {
			candidate = candidate.substring(1, candidate.length() - 1);
		}
		int zoneIndex = candidate.indexOf('%');
		if (zoneIndex >= 0) {
			candidate = candidate.substring(0, zoneIndex);
		}

		if (isValidIpv4(candidate) || isLikelyIpv6(candidate)) {
			return candidate;
		}
		return null;
	}

	private boolean isValidIpv4(String value) {
		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) {
			return false;
		}
		for (String part : parts) {
			try {
				int number = Integer.parseInt(part);
				if (number < 0 || number > 255) {
					return false;
				}
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		return true;
	}

	private boolean isLikelyIpv6(String value) {
		return value.length() <= 45
				&& value.contains(":")
				&& value.matches("[0-9A-Fa-f:.]+");
	}

	private String truncate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.length() <= MAX_HEADER_LENGTH ? value : value.substring(0, MAX_HEADER_LENGTH);
	}
}

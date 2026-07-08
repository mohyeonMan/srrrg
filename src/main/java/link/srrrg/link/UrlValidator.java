package link.srrrg.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class UrlValidator {

	private static final int MAX_URL_LENGTH = 2048;

	public void validate(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("원본 URL은 필수입니다.");
		}
		if (value.length() > MAX_URL_LENGTH) {
			throw new IllegalArgumentException("원본 URL은 2,048자 이하여야 합니다.");
		}

		URI uri = parse(value);
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw new IllegalArgumentException("HTTP 또는 HTTPS URL만 사용할 수 있습니다.");
		}

		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("호스트가 포함된 절대 URL을 입력해야 합니다.");
		}

		validatePublicHost(host);
	}

	private URI parse(String value) {
		try {
			return new URI(value);
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("유효한 URL을 입력해야 합니다.");
		}
	}

	private void validatePublicHost(String host) {
		String normalizedHost = host.toLowerCase(Locale.ROOT);
		if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
			normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
		}

		if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")) {
			throw new IllegalArgumentException("로컬 주소는 사용할 수 없습니다.");
		}
		if (normalizedHost.equals("::1") || normalizedHost.equals("0:0:0:0:0:0:0:1")) {
			throw new IllegalArgumentException("루프백 주소는 사용할 수 없습니다.");
		}

		int[] address = parseIpv4(normalizedHost);
		if (address == null) {
			return;
		}

		boolean blocked = address[0] == 0
				|| address[0] == 10
				|| address[0] == 127
				|| (address[0] == 172 && address[1] >= 16 && address[1] <= 31)
				|| (address[0] == 192 && address[1] == 168);
		if (blocked) {
			throw new IllegalArgumentException("내부망 주소는 사용할 수 없습니다.");
		}
	}

	private int[] parseIpv4(String host) {
		String[] parts = host.split("\\.", -1);
		if (parts.length != 4) {
			return null;
		}

		int[] address = new int[4];
		for (int index = 0; index < parts.length; index++) {
			try {
				address[index] = Integer.parseInt(parts[index]);
			} catch (NumberFormatException exception) {
				return null;
			}
			if (address[index] < 0 || address[index] > 255) {
				return null;
			}
		}
		return address;
	}
}

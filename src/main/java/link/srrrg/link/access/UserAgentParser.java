package link.srrrg.link.access;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class UserAgentParser {

	public UserAgentInfo parse(String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return new UserAgentInfo(null, null, null, null, "UNKNOWN", false);
		}

		String lower = userAgent.toLowerCase(Locale.ROOT);
		boolean bot = containsAny(lower, "bot", "crawler", "spider", "slurp", "headless");
		Browser browser = parseBrowser(userAgent);
		OperatingSystem os = parseOperatingSystem(userAgent);

		return new UserAgentInfo(
				browser.name(),
				browser.version(),
				os.name(),
				os.version(),
				parseDeviceType(lower, bot),
				bot
		);
	}

	private Browser parseBrowser(String value) {
		if (value.contains("Edg/")) {
			return new Browser("Edge", extractVersion(value, "Edg/"));
		}
		if (value.contains("CriOS/")) {
			return new Browser("Chrome", extractVersion(value, "CriOS/"));
		}
		if (value.contains("Chrome/")) {
			return new Browser("Chrome", extractVersion(value, "Chrome/"));
		}
		if (value.contains("FxiOS/")) {
			return new Browser("Firefox", extractVersion(value, "FxiOS/"));
		}
		if (value.contains("Firefox/")) {
			return new Browser("Firefox", extractVersion(value, "Firefox/"));
		}
		if (value.contains("Safari/") && value.contains("Version/")) {
			return new Browser("Safari", extractVersion(value, "Version/"));
		}
		if (value.toLowerCase(Locale.ROOT).contains("curl/")) {
			return new Browser("curl", extractVersion(value.toLowerCase(Locale.ROOT), "curl/"));
		}
		return new Browser("Other", null);
	}

	private OperatingSystem parseOperatingSystem(String value) {
		if (value.contains("Windows NT ")) {
			return new OperatingSystem("Windows", extractVersion(value, "Windows NT "));
		}
		if (value.contains("Android ")) {
			return new OperatingSystem("Android", extractVersion(value, "Android "));
		}
		if (value.contains("iPhone OS ")) {
			return new OperatingSystem("iOS", replaceUnderscore(extractVersion(value, "iPhone OS ")));
		}
		if (value.contains("CPU OS ")) {
			return new OperatingSystem("iPadOS", replaceUnderscore(extractVersion(value, "CPU OS ")));
		}
		if (value.contains("Mac OS X ")) {
			return new OperatingSystem("macOS", replaceUnderscore(extractVersion(value, "Mac OS X ")));
		}
		if (value.contains("Linux")) {
			return new OperatingSystem("Linux", null);
		}
		return new OperatingSystem("Other", null);
	}

	private String parseDeviceType(String lower, boolean bot) {
		if (bot) {
			return "BOT";
		}
		if (containsAny(lower, "ipad", "tablet")) {
			return "TABLET";
		}
		if (containsAny(lower, "mobile", "iphone", "android")) {
			return "MOBILE";
		}
		return "DESKTOP";
	}

	private String extractVersion(String value, String token) {
		int start = value.indexOf(token);
		if (start < 0) {
			return null;
		}
		start += token.length();
		int end = start;
		while (end < value.length()) {
			char character = value.charAt(end);
			if (!(Character.isDigit(character) || character == '.' || character == '_')) {
				break;
			}
			end++;
		}
		return end == start ? null : value.substring(start, end);
	}

	private String replaceUnderscore(String value) {
		return value == null ? null : value.replace('_', '.');
	}

	private boolean containsAny(String value, String... candidates) {
		for (String candidate : candidates) {
			if (value.contains(candidate)) {
				return true;
			}
		}
		return false;
	}

	private record Browser(String name, String version) {
	}

	private record OperatingSystem(String name, String version) {
	}
}

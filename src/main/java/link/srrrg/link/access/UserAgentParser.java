package link.srrrg.link.access;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
/**
 * User-Agent 문자열에서 브라우저, 운영체제, 기기 종류를 뽑는다. 통계 화면의 분류 축이 여기서 정해진다.
 *
 * <p>외부 라이브러리 대신 직접 판별하는 방식이라 정확도에 한계가 있다. 판별 순서가 곧 규칙인데,
 * 브라우저 대부분이 호환성 때문에 다른 브라우저의 토큰을 함께 싣기 때문이다. Edge는 Chrome 토큰을,
 * iOS의 Chrome과 Firefox는 Safari 토큰을 함께 보내므로 더 구체적인 것부터 확인해야 한다.
 * 이 순서를 바꾸면 집계 분포가 조용히 달라진다.</p>
 *
 * <p>분류에 실패한 값은 예외 대신 {@code Other}로 남긴다. 통계 기록이 리다이렉트를 막으면 안 되기 때문이다.</p>
 */
public class UserAgentParser {

	/**
	 * User-Agent를 해석한다. 값이 없으면 기기 종류만 {@code UNKNOWN}인 결과를 돌려준다.
	 *
	 * @param userAgent 요청 헤더 원문. {@code null}이어도 된다
	 * @return 분류 결과. 판별하지 못한 항목은 {@code null}이거나 {@code Other}다
	 */
	public UserAgentInfo parse(String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return new UserAgentInfo(null, null, null, null, "UNKNOWN", false);
		}

		String lower = userAgent.toLowerCase(Locale.ROOT);
		// 흔한 자동화 도구 표식만 본다. 스스로 밝히지 않는 크롤러는 걸러지지 않으므로,
		// 봇 제외 통계는 하한이 아니라 대략의 추정치로 읽어야 한다.
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

	/**
	 * 기기 종류를 정한다. 봇 판정이 가장 앞에 온다. 크롤러도 모바일 토큰을 싣는 경우가 있어
	 * 뒤에 두면 사람의 모바일 접속으로 집계된다. 태블릿을 모바일보다 먼저 보는 것도 같은 이유로,
	 * 안드로이드 태블릿의 문자열에 두 표식이 함께 들어 있기 때문이다.
	 */
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

	/**
	 * 토큰 뒤에 이어지는 숫자·점·밑줄만 버전으로 읽는다. 뒤에 무엇이 붙어 있든 거기서 끊으므로
	 * 임의 문자열이 버전 컬럼에 들어가지 않는다.
	 */
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

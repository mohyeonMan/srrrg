package link.srrrg.link.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentParserTest {

	private final UserAgentParser parser = new UserAgentParser();

	@Test
	void parsesDesktopChromeOnWindows() {
		UserAgentInfo info = parser.parse(
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
						+ "AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36"
		);

		assertThat(info.browserName()).isEqualTo("Chrome");
		assertThat(info.browserVersion()).isEqualTo("126.0.0.0");
		assertThat(info.osName()).isEqualTo("Windows");
		assertThat(info.osVersion()).isEqualTo("10.0");
		assertThat(info.deviceType()).isEqualTo("DESKTOP");
		assertThat(info.bot()).isFalse();
	}

	@Test
	void identifiesMobileSafari() {
		UserAgentInfo info = parser.parse(
				"Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
						+ "AppleWebKit/605.1.15 Version/17.5 Mobile/15E148 Safari/604.1"
		);

		assertThat(info.browserName()).isEqualTo("Safari");
		assertThat(info.osName()).isEqualTo("iOS");
		assertThat(info.osVersion()).isEqualTo("17.5");
		assertThat(info.deviceType()).isEqualTo("MOBILE");
	}

	@Test
	void identifiesBot() {
		UserAgentInfo info = parser.parse("ExampleCrawler/1.0 (+https://example.com/bot)");

		assertThat(info.deviceType()).isEqualTo("BOT");
		assertThat(info.bot()).isTrue();
	}
}

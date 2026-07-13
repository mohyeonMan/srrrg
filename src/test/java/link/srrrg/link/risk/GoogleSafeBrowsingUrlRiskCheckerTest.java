package link.srrrg.link.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleSafeBrowsingUrlRiskCheckerTest {

	private MockRestServiceServer server;
	private GoogleSafeBrowsingUrlRiskChecker checker;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		checker = new GoogleSafeBrowsingUrlRiskChecker(builder.build(), new GoogleSafeBrowsingProperties(
				"test-key", "https://safe.example/v4/threatMatches:find",
				Duration.ofSeconds(1), Duration.ofSeconds(1)));
	}

	@Test
	void sendsTheCompleteUrlAndTreatsAnEmptyResponseAsNoThreatFound() {
		String url = "https://example.com:8443/path/to?q=one%20two&x=1";
		server.expect(once(), requestTo("https://safe.example/v4/threatMatches:find?key=test-key"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().json(
						"{\"threatInfo\":{\"threatEntries\":[{\"url\":\"https://example.com:8443/path/to?q=one%20two&x=1\"}]}}",
						false))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(checker.check(url)).isEqualTo(UrlRiskCheckResult.NO_THREAT_FOUND);
		server.verify();
	}

	@Test
	void returnsThreatDetectedWhenGoogleReturnsMatches() {
		server.expect(requestTo("https://safe.example/v4/threatMatches:find?key=test-key"))
				.andRespond(withSuccess("{\"matches\":[{}]}", MediaType.APPLICATION_JSON));
		assertThat(checker.check("https://bad.example")).isEqualTo(UrlRiskCheckResult.THREAT_DETECTED);
	}

	@Test
	void returnsCheckFailedForMalformedResponse() {
		server.expect(requestTo("https://safe.example/v4/threatMatches:find?key=test-key"))
				.andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
		assertThat(checker.check("https://example.com")).isEqualTo(UrlRiskCheckResult.CHECK_FAILED);
	}

	@Test
	void returnsCheckFailedWithoutMakingARequestWhenKeyIsMissing() {
		GoogleSafeBrowsingUrlRiskChecker noKeyChecker = new GoogleSafeBrowsingUrlRiskChecker(
				RestClient.create(), new GoogleSafeBrowsingProperties("", "https://safe.example",
				Duration.ofSeconds(1), Duration.ofSeconds(1)));
		assertThat(noKeyChecker.check("https://example.com")).isEqualTo(UrlRiskCheckResult.CHECK_FAILED);
	}
}

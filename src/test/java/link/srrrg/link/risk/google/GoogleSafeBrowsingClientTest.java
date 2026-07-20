package link.srrrg.link.risk.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import link.srrrg.link.risk.RiskVerdict;

class GoogleSafeBrowsingClientTest {

	private MockRestServiceServer server;
	private GoogleSafeBrowsingClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GoogleSafeBrowsingClient(builder.build(), new GoogleSafeBrowsingProperties(
				"test-key", "https://safe.example/v5/urls:search",
				Duration.ofSeconds(1), Duration.ofSeconds(1)));
	}

	@Test
	void sendsTheCompleteUrlAndUsesTheResponseCacheDuration() {
		String url = "https://example.com:8443/path/to?q=one%20two&x=1";
		server.expect(once(), request -> {
					assertThat(request.getURI().getPath()).isEqualTo("/v5/urls:search");
					assertThat(request.getURI().getQuery()).contains("key=test-key", "urls=https://example.com");
				})
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						"{\"threats\":[],\"cacheDuration\":\"300s\"}",
						MediaType.APPLICATION_JSON));

		Instant before = Instant.now();
		var result = client.check(url);

		assertThat(result.verdict()).isEqualTo(RiskVerdict.SAFE);
		assertThat(result.verifiedAt()).isBetween(before, Instant.now());
		assertThat(result.expiresAt()).isEqualTo(result.verifiedAt().plusSeconds(300));
		server.verify();
	}

	@Test
	void returnsThreatWhenGoogleReturnsThreats() {
		server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v5/urls:search"))
				.andRespond(withSuccess("""
						{
						  "threats": [{
						    "url": "https://bad.example/",
						    "threatTypes": ["SOCIAL_ENGINEERING"]
						  }],
						  "cacheDuration": "60.5s"
						}
						""", MediaType.APPLICATION_JSON));

		var result = client.check("https://bad.example");

		assertThat(result.verdict()).isEqualTo(RiskVerdict.THREAT);
		assertThat(result.expiresAt()).isEqualTo(result.verifiedAt().plusSeconds(60).plusMillis(500));
	}

	@Test
	void limitsThreatCacheToThirtyMinutes() {
		server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v5/urls:search"))
				.andRespond(withSuccess("""
						{
						  "threats": [{"url": "https://bad.example/"}],
						  "cacheDuration": "7200s"
						}
						""", MediaType.APPLICATION_JSON));

		var result = client.check("https://bad.example");

		assertThat(result.expiresAt()).isEqualTo(result.verifiedAt().plusSeconds(1800));
	}

	@Test
	void returnsUnknownForMalformedCacheDuration() {
		server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v5/urls:search"))
				.andRespond(withSuccess(
						"{\"threats\":[],\"cacheDuration\":\"invalid\"}",
						MediaType.APPLICATION_JSON));

		assertThat(client.check("https://example.com").verdict()).isEqualTo(RiskVerdict.UNKNOWN);
	}

	@Test
	void returnsUnknownWithoutRequestWhenApiKeyIsMissing() {
		GoogleSafeBrowsingClient noKeyClient = new GoogleSafeBrowsingClient(
				RestClient.create(), new GoogleSafeBrowsingProperties("", "https://safe.example",
				Duration.ofSeconds(1), Duration.ofSeconds(1)));

		assertThat(noKeyClient.check("https://example.com").verdict()).isEqualTo(RiskVerdict.UNKNOWN);
	}
}

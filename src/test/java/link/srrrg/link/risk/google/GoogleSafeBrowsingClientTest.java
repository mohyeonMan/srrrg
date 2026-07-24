package link.srrrg.link.risk.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import com.google.protobuf.CodedOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import link.srrrg.link.risk.RiskVerdict;

class GoogleSafeBrowsingClientTest {

	private static final MediaType PROTOBUF_MEDIA_TYPE = MediaType.parseMediaType("application/x-protobuf");

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
					assertThat(request.getURI().getQuery())
							.contains("key=test-key", "urls=https://example.com")
							.doesNotContain("alt=");
				})
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Accept", PROTOBUF_MEDIA_TYPE.toString()))
				.andRespond(withSuccess(
						response(Duration.ofSeconds(300), 0),
						PROTOBUF_MEDIA_TYPE));

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
				.andRespond(withSuccess(
						response(Duration.ofMillis(60_500), 1),
						PROTOBUF_MEDIA_TYPE));

		var result = client.check("https://bad.example");

		assertThat(result.verdict()).isEqualTo(RiskVerdict.THREAT);
		assertThat(result.expiresAt()).isEqualTo(result.verifiedAt().plusSeconds(60).plusMillis(500));
	}

	@Test
	void returnsUnknownForMalformedCacheDuration() {
		server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v5/urls:search"))
				.andRespond(withSuccess(
						new byte[] {0x12, 0x01, (byte) 0x80},
						PROTOBUF_MEDIA_TYPE));

		assertThat(client.check("https://example.com").verdict()).isEqualTo(RiskVerdict.UNKNOWN);
	}

	@Test
	void returnsUnknownWhenGoogleRequestFails() {
		server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/v5/urls:search"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThat(client.check("https://example.com").verdict()).isEqualTo(RiskVerdict.UNKNOWN);
	}

	@Test
	void returnsUnknownWithoutRequestWhenApiKeyIsMissing() {
		GoogleSafeBrowsingClient noKeyClient = new GoogleSafeBrowsingClient(
				RestClient.create(), new GoogleSafeBrowsingProperties("", "https://safe.example",
				Duration.ofSeconds(1), Duration.ofSeconds(1)));

		assertThat(noKeyClient.check("https://example.com").verdict()).isEqualTo(RiskVerdict.UNKNOWN);
	}

	private byte[] response(Duration cacheDuration, int threatCount) {
		try {
			ByteArrayOutputStream body = new ByteArrayOutputStream();
			CodedOutputStream output = CodedOutputStream.newInstance(body);
			for (int index = 0; index < threatCount; index++) {
				output.writeByteArray(1, new byte[0]);
			}
			com.google.protobuf.Duration duration = com.google.protobuf.Duration.newBuilder()
					.setSeconds(cacheDuration.getSeconds())
					.setNanos(cacheDuration.getNano())
					.build();
			output.writeMessage(2, duration);
			output.flush();
			return body.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

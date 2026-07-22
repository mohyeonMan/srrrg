package link.srrrg.link.risk.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class SafeBrowsingProtobufDecoderTest {

	@Test
	void decodesGoogleSafeResponse() throws Exception {
		byte[] body = HexFormat.of().parseHex("120308ac02");

		var response = SafeBrowsingProtobufDecoder.decode(body);

		assertThat(response.threatCount()).isZero();
		assertThat(response.cacheDuration()).isEqualTo(Duration.ofSeconds(300));
	}

	@Test
	void decodesGoogleThreatResponse() throws Exception {
		byte[] body = HexFormat.of().parseHex(
				"0a310a2b746573747361666562726f7773696e672e61707073706f742e636f6d"
						+ "2f732f6d616c776172652e68746d6c12020104120308ac02");

		var response = SafeBrowsingProtobufDecoder.decode(body);

		assertThat(response.threatCount()).isOne();
		assertThat(response.cacheDuration()).isEqualTo(Duration.ofSeconds(300));
	}
}

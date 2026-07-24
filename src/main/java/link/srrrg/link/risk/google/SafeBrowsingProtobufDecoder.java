package link.srrrg.link.risk.google;

import java.io.IOException;
import java.time.Duration;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

/**
 * Safe Browsing 응답에서 위협 개수와 캐시 유효기간만 읽는다.
 */
final class SafeBrowsingProtobufDecoder {

	private static final int THREATS_FIELD_NUMBER = 1;
	private static final int CACHE_DURATION_FIELD_NUMBER = 2;

	private SafeBrowsingProtobufDecoder() {
	}

	static Response decode(byte[] body) throws IOException {
		CodedInputStream input = CodedInputStream.newInstance(body);
		int threatCount = 0;
		Duration cacheDuration = null;

		int tag;
		while ((tag = input.readTag()) != 0) {
			int fieldNumber = WireFormat.getTagFieldNumber(tag);
			if (fieldNumber != THREATS_FIELD_NUMBER && fieldNumber != CACHE_DURATION_FIELD_NUMBER) {
				if (!input.skipField(tag)) {
					break;
				}
				continue;
			}
			if (WireFormat.getTagWireType(tag) != WireFormat.WIRETYPE_LENGTH_DELIMITED) {
				throw new IOException("Invalid Safe Browsing protobuf wire type");
			}

			byte[] value = input.readByteArray();
			if (fieldNumber == THREATS_FIELD_NUMBER) {
				threatCount++;
			} else {
				cacheDuration = decodeDuration(value);
			}
		}
		return new Response(threatCount, cacheDuration);
	}

	private static Duration decodeDuration(byte[] body) throws IOException {
		com.google.protobuf.Duration value = com.google.protobuf.Duration.parseFrom(body);
		if (value.getSeconds() < 0 || value.getNanos() < 0 || value.getNanos() > 999_999_999) {
			throw new IOException("Invalid protobuf duration");
		}
		return Duration.ofSeconds(value.getSeconds(), value.getNanos());
	}

	record Response(int threatCount, Duration cacheDuration) {
	}
}

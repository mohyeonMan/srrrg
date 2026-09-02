package link.srrrg.link.risk.google;

import java.io.IOException;
import java.time.Duration;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

/**
 * Safe Browsing 응답에서 위협 개수와 캐시 유효기간만 읽는다.
 */
/**
 * Safe Browsing 응답에서 위협 개수와 캐시 유효기간만 읽는다.
 *
 * <p>생성된 protobuf 스텁 대신 필요한 두 필드만 직접 읽는다. 나머지 필드는 건너뛰므로
 * Google이 응답에 필드를 추가해도 이 해석은 그대로 동작한다. 대신 필드 번호가 코드에 박혀 있어,
 * 번호가 바뀌면 조용히 잘못 읽는 것이 아니라 위협을 못 보는 쪽으로 어긋날 수 있다.</p>
 */
final class SafeBrowsingProtobufDecoder {

	private static final int THREATS_FIELD_NUMBER = 1;
	private static final int CACHE_DURATION_FIELD_NUMBER = 2;

	private SafeBrowsingProtobufDecoder() {
	}

	/**
	 * 응답 바이트를 읽어 위협 개수와 캐시 기간을 돌려준다.
	 *
	 * <p>관심 없는 필드는 건너뛰되, 관심 있는 필드의 wire type이 예상과 다르면 예외를 낸다.
	 * 형태가 다른 응답을 억지로 해석하면 위협이 있는데 0으로 읽을 수 있어, 실패로 끝내 UNKNOWN이 되게 한다.</p>
	 *
	 * @throws IOException 응답 형태가 예상과 다른 경우. 호출자가 UNKNOWN으로 바꾼다
	 */
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

	/**
	 * 캐시 기간을 읽으면서 범위를 확인한다. 음수나 잘못된 나노초 값은 그대로 쓰면
	 * 만료 시각 계산이 어긋나 캐시가 영원히 유효하거나 즉시 만료되는 결과가 된다.
	 */
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

package link.srrrg.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 목적지 URL이 저장하고 리다이렉트해도 되는 형태인지 검사한다. 익명·프로젝트 링크를 가리지 않고
 * 모든 생성·수정 경로가 이 검사를 거치며, 리다이렉트 시점에도 다시 호출된다.
 *
 * <p>스킴을 http와 https로 제한하는 것은 {@code javascript:}나 {@code data:} 같은 값이 저장되면
 * 리다이렉트가 곧 스크립트 실행 경로가 되기 때문이다.</p>
 *
 * <p>내부망과 루프백 주소를 막는 것은 SSRF 방어다. 이 서비스는 저장된 URL을 위험 검사 대상으로 외부에 넘기고,
 * 사용자를 그 주소로 보낸다. 내부 주소를 허용하면 외부에서 접근할 수 없는 사내 서비스나 클라우드
 * 메타데이터 주소를 이 서비스를 통해 건드릴 수 있다.</p>
 *
 * <p>차단 범위는 완전하지 않다. 공개 DNS 이름이 내부 IP로 해석되는 경우는 여기서 걸러지지 않으므로,
 * 이 검사만으로 SSRF가 모두 막힌다고 보면 안 된다.</p>
 */
@Component
public class UrlValidator {

	private static final int MAX_URL_LENGTH = 2048;

	/**
	 * 순서대로 길이, 형식, 스킴, 호스트, 내부망 여부를 확인한다.
	 *
	 * @param value 검사할 목적지 URL
	 * @throws IllegalArgumentException 어느 단계든 통과하지 못한 경우. 메시지는 사용자에게 그대로 노출된다
	 */
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

	/**
	 * 호스트가 내부 주소를 가리키지 않는지 확인한다.
	 *
	 * <p>IPv6 주소를 감싼 대괄호를 먼저 벗겨야 문자열 비교가 성립한다. IP로 해석되지 않는 값은
	 * 도메인 이름으로 보고 통과시키는데, 그 이름이 내부 IP로 해석될 가능성은 여기서 다루지 않는다.
	 * 이름 해석 결과까지 검사하려면 실제 연결 시점에 다시 확인해야 한다.</p>
	 */
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

		// 사설 대역(10/8, 172.16/12, 192.168/16)과 루프백(127/8), 그리고 이 호스트를 뜻하는 0/8을 막는다.
		// 링크로서 의미가 없고 SSRF에 쓰일 수 있는 대역이다.
		boolean blocked = address[0] == 0
				|| address[0] == 10
				|| address[0] == 127
				|| (address[0] == 172 && address[1] >= 16 && address[1] <= 31)
				|| (address[0] == 192 && address[1] == 168);
		if (blocked) {
			throw new IllegalArgumentException("내부망 주소는 사용할 수 없습니다.");
		}
	}

	/**
	 * 점으로 나뉜 네 자리 십진수만 IPv4로 인정한다. 그 외에는 {@code null}을 돌려주어 도메인 이름으로 취급한다.
	 * 8진수나 정수 하나로 표기한 주소는 여기서 IP로 인식되지 않아 차단 대상에서 빠진다.
	 */
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

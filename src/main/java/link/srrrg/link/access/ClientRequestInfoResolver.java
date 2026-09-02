package link.srrrg.link.access;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
/**
 * 요청에서 통계와 레이트리밋에 쓸 클라이언트 정보를 뽑는다.
 *
 * <p>{@code X-Forwarded-For}를 기본적으로 믿지 않는 것이 중요하다. 이 헤더는 누구나 보낼 수 있어,
 * 프록시 뒤가 아닌 환경에서 그대로 받아들이면 IP 기준 레이트리밋을 헤더만 바꿔 우회할 수 있고
 * 통계도 오염된다. 신뢰할 수 있는 프록시가 앞단에 있는 환경에서만
 * {@code srrrg.trust-forwarded-headers}로 켠다.</p>
 *
 * <p>헤더 값은 길이를 잘라 담는다. 클라이언트가 보낸 문자열이라 길이 제한이 없으면
 * 저장 실패나 과도한 저장으로 이어진다.</p>
 */
public class ClientRequestInfoResolver {

	private static final int MAX_HEADER_LENGTH = 2048;

	private final boolean trustForwardedHeaders;

	public ClientRequestInfoResolver(
			@Value("${srrrg.trust-forwarded-headers:false}") boolean trustForwardedHeaders
	) {
		this.trustForwardedHeaders = trustForwardedHeaders;
	}

	public ClientRequestInfo resolve(HttpServletRequest request) {
		return new ClientRequestInfo(
				resolveIpAddress(request),
				truncate(request.getHeader("Referer")),
				truncate(request.getHeader("User-Agent"))
		);
	}

	/**
	 * 신뢰 설정이 켜져 있을 때만 프록시 헤더를 먼저 본다. 여러 IP가 이어진 값에서는 첫 항목이
	 * 원 클라이언트이며, 뒤의 항목은 중간 프록시들이다. 값이 유효하지 않으면 소켓 주소로 되돌아간다.
	 */
	private String resolveIpAddress(HttpServletRequest request) {
		if (trustForwardedHeaders) {
			String forwardedFor = request.getHeader("X-Forwarded-For");
			if (forwardedFor != null && !forwardedFor.isBlank()) {
				String forwardedIp = normalizeIp(forwardedFor.split(",", 2)[0]);
				if (forwardedIp != null) {
					return forwardedIp;
				}
			}
		}
		return normalizeIp(request.getRemoteAddr());
	}

	/**
	 * 저장할 수 있는 형태의 IP만 남기고 나머지는 {@code null}로 만든다.
	 * IPv6의 대괄호와 존 식별자를 떼는 것은 같은 주소가 표기 차이로 다른 값처럼 집계되는 것을 막기 위해서다.
	 */
	private String normalizeIp(String value) {
		if (value == null) {
			return null;
		}

		String candidate = value.trim();
		if (candidate.startsWith("[") && candidate.endsWith("]")) {
			candidate = candidate.substring(1, candidate.length() - 1);
		}
		int zoneIndex = candidate.indexOf('%');
		if (zoneIndex >= 0) {
			candidate = candidate.substring(0, zoneIndex);
		}

		if (isValidIpv4(candidate) || isLikelyIpv6(candidate)) {
			return candidate;
		}
		return null;
	}

	private boolean isValidIpv4(String value) {
		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) {
			return false;
		}
		for (String part : parts) {
			try {
				int number = Integer.parseInt(part);
				if (number < 0 || number > 255) {
					return false;
				}
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 완전한 IPv6 검증은 하지 않고 길이와 허용 문자만 확인한다. 여기서 걸러내려는 것은
	 * 잘못된 주소가 아니라 임의 문자열이 컬럼에 들어가는 것이며, 이 정도로 충분하다고 본 절충이다.
	 */
	private boolean isLikelyIpv6(String value) {
		return value.length() <= 45
				&& value.contains(":")
				&& value.matches("[0-9A-Fa-f:.]+");
	}

	private String truncate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.length() <= MAX_HEADER_LENGTH ? value : value.substring(0, MAX_HEADER_LENGTH);
	}
}

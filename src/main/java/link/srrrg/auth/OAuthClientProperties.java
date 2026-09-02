package link.srrrg.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공급자별 OAuth 자격증명 설정. 값이 없는 공급자는 등록 자체를 건너뛰므로 세 개를 모두 채울 필요는 없다.
 */
@ConfigurationProperties("srrrg.oauth")
public record OAuthClientProperties(Provider google, Provider kakao, Provider github) {

	public record Provider(String clientId, String clientSecret) {
		/**
		 * 두 값이 모두 있어야 등록 대상으로 본다. 하나만 채워진 설정은 실수일 가능성이 높고,
		 * 그대로 등록하면 실행 중에야 인증 실패로 드러난다.
		 */
		boolean configured() {
			return clientId != null && !clientId.isBlank()
					&& clientSecret != null && !clientSecret.isBlank();
		}
	}
}

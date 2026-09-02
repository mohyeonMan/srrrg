package link.srrrg.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 서명 키 설정. 활성 키와 직전 키를 함께 두어 무중단 키 교체를 지원한다.
 * 값은 설정으로만 주입하며 기본값을 두지 않는다. 키가 없으면 토큰 발급 자체가 실패해야 하기 때문이다.
 */
@ConfigurationProperties("srrrg.auth.jwt")
public record JwtProperties(
		String activeKid,
		String activeKeyBase64,
		String previousKid,
		String previousKeyBase64
) {
}

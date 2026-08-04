package link.srrrg.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("srrrg.auth.jwt")
public record JwtProperties(
		String activeKid,
		String activeKeyBase64,
		String previousKid,
		String previousKeyBase64
) {
}

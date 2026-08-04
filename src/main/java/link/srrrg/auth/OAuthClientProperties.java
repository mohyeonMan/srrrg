package link.srrrg.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("srrrg.oauth")
public record OAuthClientProperties(Provider google, Provider kakao, Provider github) {

	public record Provider(String clientId, String clientSecret) {
		boolean configured() {
			return clientId != null && !clientId.isBlank()
					&& clientSecret != null && !clientSecret.isBlank();
		}
	}
}

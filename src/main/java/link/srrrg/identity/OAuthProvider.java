package link.srrrg.identity;

import java.util.Locale;

public enum OAuthProvider {
	GOOGLE,
	KAKAO,
	GITHUB;

	public static OAuthProvider fromRegistrationId(String registrationId) {
		return valueOf(registrationId.toUpperCase(Locale.ROOT));
	}
}

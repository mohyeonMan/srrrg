package link.srrrg.identity;

import java.util.Locale;

/**
 * 지원하는 OAuth 공급자. 이름이 Spring Security 등록 id와 대소문자만 다르게 일치해야 하며,
 * DB에 문자열로 저장되므로 상수 이름을 바꾸면 기존 연결이 끊긴다.
 */
public enum OAuthProvider {
	GOOGLE,
	KAKAO,
	GITHUB;

	/**
	 * 등록 id를 enum으로 바꾼다. 목록에 없는 id는 예외로 끝나며, 이는 등록만 하고
	 * 여기 상수를 추가하지 않은 설정 실수를 로그인 시점에 드러내기 위한 것이다.
	 */
	public static OAuthProvider fromRegistrationId(String registrationId) {
		return valueOf(registrationId.toUpperCase(Locale.ROOT));
	}
}

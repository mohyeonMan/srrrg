package link.srrrg.auth;

import java.util.Map;

/**
 * 공급자별 사용자 정보 응답에서 이메일, 이메일 검증 여부, 표시 이름을 뽑아내는 규칙.
 *
 * <p>여기서 정한 {@code emailVerified}가 그대로 계정 연결 판단의 근거가 된다. 검증되지 않은 이메일을
 * 검증된 것으로 잘못 표시하면 남의 이메일을 등록한 계정이 기존 사용자와 연결될 수 있으므로,
 * 각 공급자가 명시적으로 검증했다고 알려줄 때만 참으로 둔다.</p>
 */
record ProviderProfile(String email, boolean emailVerified, String displayName) {

	/**
	 * Google은 최상위에 {@code email}과 {@code email_verified}를 준다.
	 * 이메일이 없으면 검증 여부도 의미가 없으므로 함께 거짓으로 만든다.
	 */
	static ProviderProfile google(Map<String, Object> attributes) {
		String email = string(attributes.get("email"));
		return new ProviderProfile(email, email != null && Boolean.TRUE.equals(attributes.get("email_verified")),
				string(attributes.get("name")));
	}

	/**
	 * Kakao는 이메일과 닉네임이 {@code kakao_account} 아래에 중첩돼 있고, 동의 항목에 따라 통째로 빠질 수 있다.
	 * 닉네임은 위치가 두 군데라 profile 쪽을 먼저 보고 없으면 properties에서 찾는다.
	 * 값이 없을 때 예외가 아니라 빈 Map으로 이어 가는 것은 이메일 미동의가 정상 상황이기 때문이다.
	 */
	static ProviderProfile kakao(Map<String, Object> attributes) {
		Map<?, ?> account = attributes.get("kakao_account") instanceof Map<?, ?> value ? value : Map.of();
		Object name = account.get("profile") instanceof Map<?, ?> profile
				? profile.get("nickname") : null;
		if (name == null && attributes.get("properties") instanceof Map<?, ?> properties) {
			name = properties.get("nickname");
		}
		String email = string(account.get("email"));
		return new ProviderProfile(email,
				email != null && Boolean.TRUE.equals(account.get("is_email_verified")), string(name));
	}

	/**
	 * GitHub는 별도 API로 확인한 검증된 이메일을 우선한다. 그 값이 없을 때만 프로필의 이메일을 쓰되,
	 * 이 경우는 검증되지 않은 것으로 표시한다. 프로필 이메일은 사용자가 임의로 설정할 수 있어
	 * 검증된 주소라고 볼 수 없기 때문이다.
	 */
	static ProviderProfile github(Map<String, Object> attributes, String verifiedEmail) {
		String email = verifiedEmail == null ? string(attributes.get("email")) : verifiedEmail;
		Object name = attributes.get("name") == null ? attributes.get("login") : attributes.get("name");
		return new ProviderProfile(email, verifiedEmail != null, string(name));
	}

	private static String string(Object value) {
		return value instanceof String text && !text.isBlank() ? text : null;
	}
}

package link.srrrg.auth;

import java.util.Map;

record ProviderProfile(String email, boolean emailVerified, String displayName) {

	static ProviderProfile google(Map<String, Object> attributes) {
		String email = string(attributes.get("email"));
		return new ProviderProfile(email, email != null && Boolean.TRUE.equals(attributes.get("email_verified")),
				string(attributes.get("name")));
	}

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

	static ProviderProfile github(Map<String, Object> attributes, String verifiedEmail) {
		String email = verifiedEmail == null ? string(attributes.get("email")) : verifiedEmail;
		Object name = attributes.get("name") == null ? attributes.get("login") : attributes.get("name");
		return new ProviderProfile(email, verifiedEmail != null, string(name));
	}

	private static String string(Object value) {
		return value instanceof String text && !text.isBlank() ? text : null;
	}
}

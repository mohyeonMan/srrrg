package link.srrrg.common.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class SecureRandomStringGenerator {

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate(String characters, int length) {
		if (characters == null || characters.isEmpty()) {
			throw new IllegalArgumentException("문자 집합은 비어 있을 수 없습니다.");
		}
		if (length < 1) {
			throw new IllegalArgumentException("문자열 길이는 1 이상이어야 합니다.");
		}

		StringBuilder result = new StringBuilder(length);
		for (int index = 0; index < length; index++) {
			result.append(characters.charAt(secureRandom.nextInt(characters.length())));
		}
		return result.toString();
	}
}

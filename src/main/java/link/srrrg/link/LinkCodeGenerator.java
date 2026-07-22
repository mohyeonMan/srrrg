package link.srrrg.link;

import java.util.Set;

import org.springframework.stereotype.Component;

import link.srrrg.common.util.SecureRandomStringGenerator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LinkCodeGenerator {

	static final String BASE62_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	static final int CODE_LENGTH = 6;
	private static final Set<String> RESERVED_CODES = Set.of("manage");

	private final SecureRandomStringGenerator randomStringGenerator;

	public String generate() {
		String code;
		do {
			code = randomStringGenerator.generate(BASE62_CHARACTERS, CODE_LENGTH);
		} while (RESERVED_CODES.contains(code));
		return code;
	}
}

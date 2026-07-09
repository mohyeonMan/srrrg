package link.srrrg.link;

import org.springframework.stereotype.Component;

import link.srrrg.common.util.SecureRandomStringGenerator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LinkCodeGenerator {

	static final String BASE62_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	static final int CODE_LENGTH = 6;

	private final SecureRandomStringGenerator randomStringGenerator;

	public String generate() {
		return randomStringGenerator.generate(BASE62_CHARACTERS, CODE_LENGTH);
	}
}

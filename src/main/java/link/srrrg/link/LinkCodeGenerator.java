package link.srrrg.link;

import java.util.Set;

import org.springframework.stereotype.Component;

import link.srrrg.common.util.SecureRandomStringGenerator;
import lombok.RequiredArgsConstructor;

/**
 * 단축 코드를 만든다. 62자 집합에서 6자를 뽑으므로 약 568억 가지이며,
 * 짧은 주소와 충돌·추측 가능성 사이의 절충으로 정한 길이다.
 *
 * <p>여기서는 유일성을 보장하지 않는다. 미리 조회해 확인해도 저장 사이에 다른 파드가 같은 코드를 쓸 수 있어
 * 의미가 없기 때문이다. 실제 유일성은 DB의 유일 제약이 담당하고, 충돌하면 호출자가 다시 생성해 재시도한다.</p>
 */
@Component
@RequiredArgsConstructor
public class LinkCodeGenerator {

	static final String BASE62_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	static final int CODE_LENGTH = 6;
	// 실제 경로와 겹치면 그 화면 대신 리다이렉트가 잡히므로 제외한다.
	// 여섯 글자 경로를 새로 만들 때 이 목록에도 추가해야 한다.
	private static final Set<String> RESERVED_CODES = Set.of("manage");

	private final SecureRandomStringGenerator randomStringGenerator;

	/**
	 * 예약어가 아닌 코드가 나올 때까지 다시 뽑는다. 예약어는 한 줌이고 후보는 수백억 가지라
	 * 반복이 길어질 일은 없다. 이 코드가 이미 쓰이고 있는지는 확인하지 않는다.
	 */
	public String generate() {
		String code;
		do {
			code = randomStringGenerator.generate(BASE62_CHARACTERS, CODE_LENGTH);
		} while (RESERVED_CODES.contains(code));
		return code;
	}
}

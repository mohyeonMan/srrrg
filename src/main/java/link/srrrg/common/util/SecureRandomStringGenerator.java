package link.srrrg.common.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
/**
 * 추측되면 안 되는 문자열의 유일한 생성원. 단축 코드, 링크 secret key, 프로젝트 API 키,
 * refresh token, OAuth state와 계정 연결 토큰이 모두 이 클래스를 거친다.
 *
 * <p>{@link SecureRandom}을 쓰는 이유는 이 값들이 곧 인증 수단이기 때문이다. 예측 가능한 난수를 쓰면
 * 단축 코드는 열거로 전수 조회가 가능해지고, secret key와 API 키는 추측만으로 남의 링크를 수정할 수 있게 된다.
 * 속도를 이유로 {@code Math.random()}이나 {@code java.util.Random}으로 바꾸지 않는다.</p>
 *
 * <p>{@code SecureRandom}은 스레드 안전하므로 싱글턴 빈에서 인스턴스를 공유해도 된다.</p>
 */
public class SecureRandomStringGenerator {

	private final SecureRandom secureRandom = new SecureRandom();

	/**
	 * 주어진 문자 집합에서 균등하게 뽑은 문자로 길이가 {@code length}인 문자열을 만든다.
	 *
	 * @param characters 사용할 문자 집합. 중복 문자가 들어 있으면 그만큼 그 문자가 더 자주 뽑혀
	 *                   실제 엔트로피가 호출자의 계산보다 낮아진다. 중복 없는 집합을 넘겨야 한다
	 * @param length 생성할 문자 수. 이 값이 곧 엔트로피를 결정하므로 호출자가 용도에 맞게 정한다
	 * @return 생성된 문자열
	 * @throws IllegalArgumentException 문자 집합이 비었거나 길이가 1 미만인 경우
	 */
	public String generate(String characters, int length) {
		if (characters == null || characters.isEmpty()) {
			throw new IllegalArgumentException("문자 집합은 비어 있을 수 없습니다.");
		}
		if (length < 1) {
			throw new IllegalArgumentException("문자열 길이는 1 이상이어야 합니다.");
		}

		// nextInt(bound)는 나머지 연산 방식과 달리 편향 없는 균등 분포를 보장하므로 별도 보정이 필요 없다.
		StringBuilder result = new StringBuilder(length);
		for (int index = 0; index < length; index++) {
			result.append(characters.charAt(secureRandom.nextInt(characters.length())));
		}
		return result.toString();
	}
}

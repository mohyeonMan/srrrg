package link.srrrg.identity;

import java.time.Instant;
import java.util.List;

/**
 * 로그인 사용자의 계정 관리 화면에 필요한 application 결과다. 공급자 식별자와 공급자 이메일은
 * 제외하고, 현재 계정에 연결돼 로그인에 사용할 수 있는 공급자 이름만 포함한다.
 */
public record AccountProfile(
		Long id,
		String email,
		String displayName,
		List<String> providers,
		Instant createdAt) {
}

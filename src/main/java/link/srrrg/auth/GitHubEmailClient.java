package link.srrrg.auth;

import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * GitHub 사용자 정보 응답만으로는 검증된 이메일을 알 수 없어 이메일 목록 API를 따로 호출한다.
 * 로그인 처리 도중 외부 호출이 하나 늘어나는 대가로, 검증되지 않은 주소가 계정 연결 판단에
 * 쓰이는 것을 막는다.
 */
@Component
class GitHubEmailClient {

	private final RestClient restClient = RestClient.builder()
			.baseUrl("https://api.github.com")
			.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
			.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
			.build();

	String verifiedEmail(String accessToken) {
		GitHubEmail[] emails = restClient.get()
				.uri("/user/emails")
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve()
				.body(GitHubEmail[].class);
		if (emails == null) {
			return null;
		}
		return selectVerifiedPrimary(emails);
	}

	/**
	 * 기본이면서 검증까지 된 주소만 고른다. 둘 중 하나라도 빠지면 이 사용자의 신원으로 쓰지 않으며,
	 * 해당하는 주소가 없으면 {@code null}을 돌려줘 검증되지 않은 로그인으로 이어진다.
	 */
	static String selectVerifiedPrimary(GitHubEmail[] emails) {
		return Arrays.stream(emails)
				.filter(email -> email.primary() && email.verified())
				.map(GitHubEmail::email)
				.findFirst()
				.orElse(null);
	}

	record GitHubEmail(String email, boolean primary, boolean verified) {
	}
}

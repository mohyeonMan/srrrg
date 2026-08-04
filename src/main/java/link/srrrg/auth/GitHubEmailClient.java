package link.srrrg.auth;

import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

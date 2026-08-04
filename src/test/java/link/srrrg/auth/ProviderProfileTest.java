package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ProviderProfileTest {

	@Test
	void keepsEmailAndVerificationStatusSeparate() {
		ProviderProfile verified = ProviderProfile.google(Map.of(
				"email", "google@example.com", "email_verified", true, "name", "Google User"));
		ProviderProfile unverified = ProviderProfile.google(Map.of(
				"email", "unverified@example.com", "email_verified", false));
		ProviderProfile missing = ProviderProfile.google(Map.of("name", "No Email"));

		assertThat(verified.email()).isEqualTo("google@example.com");
		assertThat(verified.emailVerified()).isTrue();
		assertThat(unverified.email()).isEqualTo("unverified@example.com");
		assertThat(unverified.emailVerified()).isFalse();
		assertThat(missing.email()).isNull();
		assertThat(missing.emailVerified()).isFalse();
	}

	@Test
	void acceptsKakaoAndGitHubProfilesWithoutEmail() {
		ProviderProfile kakao = ProviderProfile.kakao(Map.of(
				"properties", Map.of("nickname", "Kakao User")));
		ProviderProfile github = ProviderProfile.github(Map.of("login", "octocat"), null);

		assertThat(kakao.email()).isNull();
		assertThat(kakao.displayName()).isEqualTo("Kakao User");
		assertThat(github.email()).isNull();
		assertThat(github.displayName()).isEqualTo("octocat");
	}

	@Test
	void githubRequiresPrimaryAndVerifiedEmailForVerifiedStatus() {
		GitHubEmailClient.GitHubEmail[] emails = {
				new GitHubEmailClient.GitHubEmail("verified-not-primary@example.com", false, true),
				new GitHubEmailClient.GitHubEmail("primary-not-verified@example.com", true, false),
				new GitHubEmailClient.GitHubEmail("selected@example.com", true, true)
		};

		assertThat(GitHubEmailClient.selectVerifiedPrimary(emails)).isEqualTo("selected@example.com");
	}
}

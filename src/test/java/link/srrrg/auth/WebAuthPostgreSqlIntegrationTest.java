package link.srrrg.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import link.srrrg.auth.WebSessionService.SessionTokens;
import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthIdentityService;
import link.srrrg.identity.OAuthIdentityService.LoginResolution;
import link.srrrg.identity.OAuthProvider;
import link.srrrg.identity.OAuthAccountRepository;
import link.srrrg.project.ApiKeyScope;
import link.srrrg.project.ApiKeyService;
import link.srrrg.project.ProjectMemberRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WebAuthPostgreSqlIntegrationTest {

	private static final String JWT_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("srrrg.auth.jwt.active-kid", () -> "test");
		registry.add("srrrg.auth.jwt.active-key-base64", () -> JWT_KEY);
		registry.add("srrrg.oauth.google.client-id", () -> "google-client");
		registry.add("srrrg.oauth.google.client-secret", () -> "google-secret");
		registry.add("srrrg.oauth.kakao.client-id", () -> "kakao-client");
		registry.add("srrrg.oauth.kakao.client-secret", () -> "kakao-secret");
		registry.add("srrrg.oauth.github.client-id", () -> "github-client");
		registry.add("srrrg.oauth.github.client-secret", () -> "github-secret");
		registry.add("srrrg.url-risk.provider", () -> "fixed-safe");
	}

	@Autowired
	OAuthIdentityService identityService;

	@Autowired
	OAuthAccountLinkService accountLinkService;

	@Autowired
	WebSessionService sessionService;

	@Autowired
	RefreshTokenService refreshTokenService;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	OAuthAuthorizationRequestRepository authorizationRequestRepository;

	@Autowired
	OAuthAccountRepository accountRepository;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApiKeyService apiKeyService;

	@Autowired
	ProjectMemberRepository projectMemberRepository;

	@Test
	void linksProviderOnlyAfterExistingAccountLogin() {
		LoginResolution google = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "google-1", "same@example.com"));
		LoginResolution kakao = identityService.resolve(identity(
				OAuthProvider.KAKAO, "kakao-1", "same@example.com"));

		assertThat(kakao.requiresLink()).isTrue();
		OAuthAccountLinkService.PendingLink pending = accountLinkService.create(
				kakao.user(), kakao.pendingIdentity(), "/manage");
		assertThat(accountLinkService.complete(pending.rawToken(), google.user())).isEqualTo("/manage");

		LoginResolution linked = identityService.resolve(identity(
				OAuthProvider.KAKAO, "kakao-1", "same@example.com"));
		assertThat(linked.requiresLink()).isFalse();
		assertThat(linked.user().getId()).isEqualTo(google.user().getId());

		LoginResolution github = identityService.resolve(identity(
				OAuthProvider.GITHUB, "github-1", "same@example.com"));
		OAuthAccountLinkService.PendingLink githubPending = accountLinkService.create(
				github.user(), github.pendingIdentity(), "/");
		accountLinkService.complete(githubPending.rawToken(), google.user());
		assertThat(identityService.resolve(identity(
				OAuthProvider.GITHUB, "github-1", "same@example.com")).user().getId())
				.isEqualTo(google.user().getId());
	}

	@Test
	void allowsLoginWithoutEmailAndNeverMergesUnverifiedEmail() {
		LoginResolution verified = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "verified-owner", "same-unverified@example.com"));
		LoginResolution missing = identityService.resolve(new OAuthIdentity(
				OAuthProvider.KAKAO, "kakao-no-email", null, false, null));
		LoginResolution repeated = identityService.resolve(new OAuthIdentity(
				OAuthProvider.KAKAO, "kakao-no-email", null, false, null));
		LoginResolution unverified = identityService.resolve(new OAuthIdentity(
				OAuthProvider.GITHUB, "github-unverified", "same-unverified@example.com", false, "GitHub User"));

		assertThat(missing.requiresLink()).isFalse();
		assertThat(missing.user().getEmail()).isNull();
		assertThat(missing.user().getEmailVerifiedAt()).isNull();
		assertThat(missing.user().getDisplayName()).isEqualTo("srrrg 사용자");
		assertThat(repeated.user().getId()).isEqualTo(missing.user().getId());
		assertThat(unverified.requiresLink()).isFalse();
		assertThat(unverified.user().getId()).isNotEqualTo(verified.user().getId());
		assertThat(unverified.user().getEmail()).isNull();
		assertThat(accountRepository.findByProviderAndProviderUserId(
				OAuthProvider.GITHUB, "github-unverified").orElseThrow().isProviderEmailVerified()).isFalse();
	}

	@Test
	void rotatesRefreshTokenAndRevokesFamilyOnReuse() {
		LoginResolution login = identityService.resolve(identity(
				OAuthProvider.GITHUB, "github-refresh", "refresh@example.com"));
		SessionTokens initial = sessionService.issue(login.user());
		SessionTokens rotated = sessionService.refresh(initial.refreshToken());

		assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());
		assertThatThrownBy(() -> sessionService.refresh(initial.refreshToken()))
				.isInstanceOf(RefreshTokenReuseException.class);
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getUser().getId().equals(login.user().getId()))
				.allMatch(token -> token.getRevokedAt() != null);
	}

	@Test
	void supportsCurrentDeviceAndAllDeviceLogout() {
		LoginResolution login = identityService.resolve(identity(
				OAuthProvider.GOOGLE, "google-logout", "logout@example.com"));
		SessionTokens firstDevice = sessionService.issue(login.user());
		SessionTokens secondDevice = sessionService.issue(login.user());

		refreshTokenService.logoutCurrent(firstDevice.refreshToken());
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getTokenHash().equals(TokenHash.sha256(firstDevice.refreshToken())))
				.allMatch(token -> token.getRevokedAt() != null);
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getTokenHash().equals(TokenHash.sha256(secondDevice.refreshToken())))
				.allMatch(token -> token.getRevokedAt() == null);

		refreshTokenService.logoutAll(secondDevice.refreshToken());
		assertThat(refreshTokenRepository.findAll())
				.filteredOn(token -> token.getUser().getId().equals(login.user().getId()))
				.allMatch(token -> token.getRevokedAt() != null);
	}

	@Test
	void preservesAnonymousRoutesAndProtectsCookiePostWithCsrf() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Google로 계속")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Kakao로 계속")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("GitHub로 계속")));

		mockMvc.perform(post("/api/links")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/web/auth/logout"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@Test
	void startsAllProviderLoginsWithDatabaseStateAndPkce() throws Exception {
		assertAuthorizationRedirect("google", "https://accounts.google.com/o/oauth2/v2/auth");
		assertAuthorizationRedirect("kakao", "https://kauth.kakao.com/oauth/authorize");
		assertAuthorizationRedirect("github", "https://github.com/login/oauth/authorize");
		assertThat(authorizationRequestRepository.count()).isGreaterThanOrEqualTo(3);
	}

	@Test
	void allowsOnlyMatchingProjectAndActiveApiKeyScope() throws Exception {
		LoginResolution login = identityService.resolve(identity(OAuthProvider.GOOGLE, "api-key-user", "api-key@example.com"));
		Long projectId = projectMemberRepository.findByIdUserId(login.user().getId()).getFirst().getProject().getId();
		ApiKeyService.CreatedKey created = apiKeyService.create(login.user().getId(), projectId, "automation", java.util.Set.of(ApiKeyScope.LINKS_READ), null);

		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + created.rawKey()))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId + 1)
					.header("Authorization", "Bearer " + created.rawKey()))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"));
		apiKeyService.revoke(login.user().getId(), projectId, created.key().getId());
		mockMvc.perform(get("/api/v1/projects/{projectId}/links", projectId)
					.header("Authorization", "Bearer " + created.rawKey()))
				.andExpect(status().isUnauthorized());
	}

	private void assertAuthorizationRedirect(String provider, String authorizationUri) throws Exception {
		mockMvc.perform(get("/oauth2/authorization/{provider}", provider))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(authorizationUri)))
				.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge=")))
				.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge_method=S256")))
				.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
						org.hamcrest.Matchers.containsString("srrrg_oauth_request="))));
	}

	private OAuthIdentity identity(OAuthProvider provider, String providerUserId, String email) {
		return new OAuthIdentity(provider, providerUserId, email, true, "Test User");
	}
}

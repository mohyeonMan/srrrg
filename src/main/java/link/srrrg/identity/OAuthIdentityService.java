package link.srrrg.identity;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import link.srrrg.project.ProjectService;

@Service
@RequiredArgsConstructor
public class OAuthIdentityService {

	private final UserRepository userRepository;
	private final OAuthAccountRepository accountRepository;
	private final ProjectService projectService;

	@Transactional
	public LoginResolution resolve(OAuthIdentity suppliedIdentity) {
		OAuthIdentity identity = normalize(suppliedIdentity);
		LoginResolution resolution = accountRepository.findByProviderAndProviderUserId(
				identity.provider(), identity.providerUserId())
				.map(account -> existingAccount(account, identity))
				.orElseGet(() -> newAccount(identity));
		if (!resolution.requiresLink()) {
			projectService.ensurePersonalProject(resolution.user().getId());
		}
		return resolution;
	}

	@Transactional
	public void attach(User user, OAuthIdentity suppliedIdentity) {
		OAuthIdentity identity = normalize(suppliedIdentity);
		accountRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
				.ifPresentOrElse(account -> {
					if (!account.getUser().getId().equals(user.getId())) {
						throw new IllegalStateException("이미 다른 사용자에게 연결된 OAuth 계정입니다.");
					}
				}, () -> accountRepository.save(OAuthAccount.create(user, identity)));
	}

	private LoginResolution existingAccount(OAuthAccount account, OAuthIdentity identity) {
		account.recordLogin(identity.providerEmail(), identity.providerEmailVerified());
		return LoginResolution.authenticated(account.getUser());
	}

	private LoginResolution newAccount(OAuthIdentity identity) {
		String verifiedEmail = identity.verifiedEmail();
		if (verifiedEmail == null) {
			return createUser(identity, null);
		}
		return userRepository.findByEmailAndEmailVerifiedAtIsNotNull(verifiedEmail)
				.<LoginResolution>map(user -> LoginResolution.linkRequired(user, identity))
				.orElseGet(() -> createUser(identity, verifiedEmail));
	}

	private LoginResolution createUser(OAuthIdentity identity, String verifiedEmail) {
		User user = userRepository.save(User.create(verifiedEmail, identity.displayName()));
		accountRepository.save(OAuthAccount.create(user, identity));
		return LoginResolution.authenticated(user);
	}

	private OAuthIdentity normalize(OAuthIdentity identity) {
		if (identity.provider() == null || identity.providerUserId() == null
				|| identity.providerUserId().isBlank() || identity.providerUserId().length() > 255) {
			throw new IllegalArgumentException("OAuth 사용자 식별자가 올바르지 않습니다.");
		}
		String email = normalizeEmail(identity.providerEmail());
		boolean emailVerified = identity.providerEmailVerified() && email != null;
		String name = identity.displayName() == null || identity.displayName().isBlank()
				? defaultDisplayName(email)
				: identity.displayName().trim();
		if (name.length() > 100) {
			name = name.substring(0, 100);
		}
		return new OAuthIdentity(identity.provider(), identity.providerUserId(), email, emailVerified, name);
	}

	private String normalizeEmail(String suppliedEmail) {
		if (suppliedEmail == null || suppliedEmail.isBlank()) {
			return null;
		}
		String email = suppliedEmail.trim().toLowerCase(Locale.ROOT);
		int at = email.indexOf('@');
		return email.length() <= 320 && at > 0 && at < email.length() - 1 ? email : null;
	}

	private String defaultDisplayName(String email) {
		return email == null ? "srrrg 사용자" : email.substring(0, email.indexOf('@'));
	}

	public record LoginResolution(User user, OAuthIdentity pendingIdentity) {
		public static LoginResolution authenticated(User user) {
			return new LoginResolution(user, null);
		}

		public static LoginResolution linkRequired(User user, OAuthIdentity identity) {
			return new LoginResolution(user, identity);
		}

		public boolean requiresLink() {
			return pendingIdentity != null;
		}
	}
}

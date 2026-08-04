package link.srrrg.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthProvider;
import link.srrrg.identity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "oauth_account_link_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAccountLinkRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "existing_user_id", nullable = false)
	private Long existingUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OAuthProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	@Column(name = "provider_email", nullable = false, length = 320)
	private String providerEmail;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "return_path", nullable = false, length = 1000)
	private String returnPath;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private OAuthAccountLinkRequest(String tokenHash, User user, OAuthIdentity identity,
			String returnPath, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.existingUserId = user.getId();
		this.provider = identity.provider();
		this.providerUserId = identity.providerUserId();
		this.providerEmail = identity.verifiedEmail();
		this.displayName = identity.displayName();
		this.returnPath = returnPath;
		this.expiresAt = expiresAt;
	}

	public static OAuthAccountLinkRequest create(String tokenHash, User user, OAuthIdentity identity,
			String returnPath, Instant expiresAt) {
		return new OAuthAccountLinkRequest(tokenHash, user, identity, returnPath, expiresAt);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public OAuthIdentity identity() {
		return new OAuthIdentity(provider, providerUserId, providerEmail, true, displayName);
	}
}

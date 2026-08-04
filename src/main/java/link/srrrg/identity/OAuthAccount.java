package link.srrrg.identity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "oauth_accounts", uniqueConstraints =
		@UniqueConstraint(name = "uq_oauth_accounts_provider_user", columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OAuthProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	@Column(name = "provider_email", length = 320)
	private String providerEmail;

	@Column(name = "provider_email_verified", nullable = false)
	private boolean providerEmailVerified;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_login_at", nullable = false)
	private Instant lastLoginAt;

	private OAuthAccount(User user, OAuthIdentity identity) {
		this.user = user;
		this.provider = identity.provider();
		this.providerUserId = identity.providerUserId();
		this.providerEmail = identity.providerEmail();
		this.providerEmailVerified = identity.providerEmailVerified();
	}

	public static OAuthAccount create(User user, OAuthIdentity identity) {
		return new OAuthAccount(user, identity);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		lastLoginAt = createdAt;
	}

	public void recordLogin(String email, boolean emailVerified) {
		providerEmail = email;
		providerEmailVerified = emailVerified;
		lastLoginAt = Instant.now();
	}
}

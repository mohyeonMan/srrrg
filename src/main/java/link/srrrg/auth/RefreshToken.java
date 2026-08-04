package link.srrrg.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import link.srrrg.identity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "token_family_id", nullable = false)
	private UUID tokenFamilyId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by_token_id")
	private Long replacedByTokenId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.tokenFamilyId = familyId;
		this.expiresAt = expiresAt;
	}

	public static RefreshToken create(User user, String tokenHash, UUID familyId, Instant expiresAt) {
		return new RefreshToken(user, tokenHash, familyId, expiresAt);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public void replaceWith(Long replacementId, Instant now) {
		usedAt = now;
		replacedByTokenId = replacementId;
	}

	public boolean expiredAt(Instant now) {
		return !expiresAt.isAfter(now);
	}
}

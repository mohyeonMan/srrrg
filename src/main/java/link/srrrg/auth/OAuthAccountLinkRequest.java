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
/**
 * 기존 계정에 새 공급자 계정을 붙여도 되는지 사용자에게 확인받는 동안 남겨 두는 대기 요청.
 *
 * <p>확정 전이므로 아직 어떤 연결도 만들어지지 않은 상태다. 여기 담긴 신원 값은 사용자가 기존 방식으로
 * 다시 로그인해 본인임을 증명한 뒤에야 실제 연결로 옮겨진다.</p>
 *
 * <p>{@code existingUserId}와 {@code providerEmail}은 확정 시점의 대조용이다. 대기 요청을 만든 뒤
 * 계정 상태가 바뀌었으면 연결을 거부해야 하므로 두 값을 그대로 보관한다.</p>
 */
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

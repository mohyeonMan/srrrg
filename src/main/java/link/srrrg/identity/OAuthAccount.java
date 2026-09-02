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

/**
 * 사용자 한 명과 공급자 계정 하나의 연결. 한 사용자가 여러 공급자를 붙일 수 있고,
 * 이 표의 존재 여부가 곧 그 공급자로 로그인할 수 있는지를 결정한다.
 *
 * <p>(provider, provider_user_id) 유일 제약이 하나의 공급자 계정이 두 사용자에게 연결되는 것을 막는다.
 * 서비스 계층에서도 같은 검사를 하지만, 동시 요청에서는 애플리케이션 검사만으로 막을 수 없어 DB 제약이 최종 방어선이다.</p>
 */
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

	/**
	 * 로그인할 때마다 공급자 쪽 이메일과 검증 여부를 최신값으로 덮어쓴다.
	 * 이 값은 공급자에서의 상태를 비추는 기록일 뿐이며, 사용자 계정의 이메일과는 별개다.
	 */
	public void recordLogin(String email, boolean emailVerified) {
		providerEmail = email;
		providerEmailVerified = emailVerified;
		lastLoginAt = Instant.now();
	}
}

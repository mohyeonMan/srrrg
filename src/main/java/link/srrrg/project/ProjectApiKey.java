package link.srrrg.project;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
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

/**
 * 발급된 프로젝트 API 키 한 건. 사용자가 아니라 프로젝트에 묶이므로, 키를 만든 사람이 프로젝트를 떠나도
 * 키는 계속 동작한다.
 *
 * <p>{@code keyPrefix}는 목록에서 키를 식별하기 위한 평문이고, 비밀은 {@code keyHash}뿐이다.
 * scope를 즉시 로딩하는 것은 인증 때마다 함께 필요하기 때문이다.</p>
 */
@Entity
@Table(name = "project_api_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectApiKey {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	// @SoftDelete 엔티티를 가리키는 to-one 연관은 LAZY로 둘 수 없다.
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;
	@Column(nullable = false, length = 100)
	private String name;
	@Column(name = "key_prefix", nullable = false, length = 32)
	private String keyPrefix;
	@Column(name = "key_hash", nullable = false, length = 64)
	private String keyHash;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id", nullable = false)
	private User createdBy;
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "project_api_key_scopes", joinColumns = @JoinColumn(name = "api_key_id"))
	@Convert(converter = ApiKeyScopeConverter.class)
	@Column(name = "scope", nullable = false, length = 32)
	private Set<ApiKeyScope> scopes = EnumSet.noneOf(ApiKeyScope.class);
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "last_used_at")
	private Instant lastUsedAt;
	@Column(name = "expires_at")
	private Instant expiresAt;
	@Column(name = "revoked_at")
	private Instant revokedAt;

	private ProjectApiKey(Project project, String name, String keyPrefix, String keyHash, User createdBy,
			Set<ApiKeyScope> scopes, Instant expiresAt) {
		this.project = project;
		this.name = name;
		this.keyPrefix = keyPrefix;
		this.keyHash = keyHash;
		this.createdBy = createdBy;
		this.scopes = EnumSet.copyOf(scopes);
		this.expiresAt = expiresAt;
	}

	public static ProjectApiKey create(Project project, String name, String prefix, String hash, User user,
			Set<ApiKeyScope> scopes, Instant expiresAt) {
		return new ProjectApiKey(project, name, prefix, hash, user, scopes, expiresAt);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	/**
	 * 폐기되지 않았고 만료 전이어야 쓸 수 있다. 만료 시각이 없으면 무기한 유효한 키다.
	 */
	public boolean isUsableAt(Instant now) {
		return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
	}

	public void recordUse() {
		lastUsedAt = Instant.now();
	}

	/**
	 * 최초 폐기 시각을 보존한다. 다시 폐기해도 시각을 갱신하지 않아, 언제부터 무효였는지가 유지된다.
	 */
	public void revoke() {
		if (revokedAt == null)
			revokedAt = Instant.now();
	}
}

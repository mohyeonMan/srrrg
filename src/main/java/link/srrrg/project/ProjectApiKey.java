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

@Entity
@Table(name = "project_api_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectApiKey {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false)
	private Project project;
	@Column(nullable = false, length = 100)
	private String name;
	@Column(name = "key_prefix", nullable = false, length = 32)
	private String keyPrefix;
	@Column(name = "key_hash", nullable = false, length = 64)
	private String keyHash;
	@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id", nullable = false)
	private User createdBy;
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "project_api_key_scopes", joinColumns = @JoinColumn(name = "api_key_id"))
	@Convert(converter = ApiKeyScopeConverter.class)
	@Column(name = "scope", nullable = false, length = 32)
	private Set<ApiKeyScope> scopes = EnumSet.noneOf(ApiKeyScope.class);
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "last_used_at") private Instant lastUsedAt;
	@Column(name = "expires_at") private Instant expiresAt;
	@Column(name = "revoked_at") private Instant revokedAt;

	private ProjectApiKey(Project project, String name, String keyPrefix, String keyHash, User createdBy,
			Set<ApiKeyScope> scopes, Instant expiresAt) {
		this.project = project; this.name = name; this.keyPrefix = keyPrefix; this.keyHash = keyHash;
		this.createdBy = createdBy; this.scopes = EnumSet.copyOf(scopes); this.expiresAt = expiresAt;
	}
	public static ProjectApiKey create(Project project, String name, String prefix, String hash, User user,
			Set<ApiKeyScope> scopes, Instant expiresAt) {
		return new ProjectApiKey(project, name, prefix, hash, user, scopes, expiresAt);
	}
	@PrePersist void onCreate() { createdAt = Instant.now(); }
	public boolean isUsableAt(Instant now) { return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now)); }
	public void recordUse() { lastUsedAt = Instant.now(); }
	public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }
}

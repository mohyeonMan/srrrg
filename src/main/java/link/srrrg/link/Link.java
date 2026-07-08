package link.srrrg.link;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "links")
public class Link {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 6)
	private String code;

	@Column(name = "original_url", nullable = false, length = 2048)
	private String originalUrl;

	@Column(name = "secret_key_hash", nullable = false, length = 100)
	private String secretKeyHash;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "click_count", nullable = false)
	private long clickCount;

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Link() {
	}

	private Link(String code, String originalUrl, String secretKeyHash, Instant expiresAt) {
		this.code = code;
		this.originalUrl = originalUrl;
		this.secretKeyHash = secretKeyHash;
		this.expiresAt = expiresAt;
		this.clickCount = 0;
		this.deleted = false;
	}

	public static Link create(String code, String originalUrl, String secretKeyHash, Instant expiresAt) {
		return new Link(code, originalUrl, secretKeyHash, expiresAt);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public String getCode() {
		return code;
	}

	public String getOriginalUrl() {
		return originalUrl;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean isExpiredAt(Instant instant) {
		return expiresAt != null && !expiresAt.isAfter(instant);
	}
}

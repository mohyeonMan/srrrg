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
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import link.srrrg.campaign.Campaign;
import link.srrrg.campaign.UtmTemplate;
import link.srrrg.project.Project;
import link.srrrg.identity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Link {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 6)
	private String code;

	@Column(name = "original_url", length = 2048)
	private String originalUrl;

	@Column(name = "secret_key_hash", length = 100)
	private String secretKeyHash;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@Column(length = 63)
	private String subdomain;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id")
	private User createdBy;

	@Column(name = "idempotency_api_key_id")
	private Long idempotencyApiKeyId;
	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;
	@Column(name = "idempotency_request_hash", length = 64)
	private String idempotencyRequestHash;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "campaign_id")
	private Campaign campaign;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "utm_template_id")
	private UtmTemplate utmTemplate;

	@Column(name = "external_id", length = 100)
	private String externalId;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private Link(String code, String originalUrl, String secretKeyHash, Instant expiresAt) {
		this.code = code;
		this.originalUrl = originalUrl;
		this.secretKeyHash = secretKeyHash;
		this.expiresAt = expiresAt;
		this.deleted = false;
	}

	public static Link create(String code, String originalUrl, String secretKeyHash, Instant expiresAt) {
		return new Link(code, originalUrl, secretKeyHash, expiresAt);
	}

	public static Link createForProject(String code, String originalUrl, Instant expiresAt,
			Project project, String subdomain, User createdBy, Long apiKeyId, String idempotencyKey, String requestHash) {
		return createForCampaign(code, originalUrl, expiresAt, project, subdomain, createdBy, apiKeyId, idempotencyKey, requestHash,
				null, null, null);
	}

	public static Link createForCampaign(String code, String originalUrl, Instant expiresAt,
			Project project, String subdomain, User createdBy, Long apiKeyId, String idempotencyKey, String requestHash,
			Campaign campaign, UtmTemplate utmTemplate, String externalId) {
		Link link = new Link(code, originalUrl, null, expiresAt);
		link.project = project;
		link.subdomain = subdomain;
		link.createdBy = createdBy;
		link.idempotencyApiKeyId = apiKeyId;
		link.idempotencyKey = idempotencyKey;
		link.idempotencyRequestHash = requestHash;
		link.campaign = campaign;
		link.utmTemplate = utmTemplate;
		link.externalId = externalId;
		return link;
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

	public boolean isExpiredAt(Instant instant) {
		return expiresAt != null && !expiresAt.isAfter(instant);
	}

	public void updateOriginalUrl(String originalUrl) {
		this.originalUrl = originalUrl;
		this.updatedAt = Instant.now();
	}

	public void updateExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
		this.updatedAt = Instant.now();
	}

	public void delete() {
		this.deleted = true;
		this.updatedAt = Instant.now();
	}

	public void assignToProject(Project project, User createdBy) {
		this.project = project;
		this.createdBy = createdBy;
		this.secretKeyHash = null;
		this.updatedAt = Instant.now();
	}
}

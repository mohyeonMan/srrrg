package link.srrrg.campaign.importing;

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
import link.srrrg.campaign.Campaign;
import link.srrrg.campaign.UtmTemplate;
import link.srrrg.identity.User;
import link.srrrg.project.Project;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_imports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignImport {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false)
	private Campaign campaign;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "utm_template_id")
	private UtmTemplate utmTemplate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImportStatus status;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;
	@Column(name = "idempotency_request_hash", nullable = false, length = 64)
	private String idempotencyRequestHash;

	@Column(name = "total_rows", nullable = false)
	private int totalRows;
	@Column(name = "processed_rows", nullable = false)
	private int processedRows;
	@Column(name = "succeeded_rows", nullable = false)
	private int succeededRows;
	@Column(name = "failed_rows", nullable = false)
	private int failedRows;

	@Column(name = "lease_owner", length = 100)
	private String leaseOwner;
	@Column(name = "lease_expires_at")
	private Instant leaseExpiresAt;
	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id")
	private User createdBy;
	@Column(name = "created_by_api_key_id")
	private Long createdByApiKeyId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "completed_at")
	private Instant completedAt;

	private CampaignImport(Campaign campaign, Project project, UtmTemplate utmTemplate, int totalRows,
			String idempotencyKey, String idempotencyRequestHash, User createdBy, Long createdByApiKeyId) {
		this.campaign = campaign;
		this.project = project;
		this.utmTemplate = utmTemplate;
		this.status = ImportStatus.PENDING;
		this.totalRows = totalRows;
		this.idempotencyKey = idempotencyKey;
		this.idempotencyRequestHash = idempotencyRequestHash;
		this.createdBy = createdBy;
		this.createdByApiKeyId = createdByApiKeyId;
	}

	public static CampaignImport create(Campaign campaign, Project project, UtmTemplate utmTemplate, int totalRows,
			String idempotencyKey, String idempotencyRequestHash, User createdBy, Long createdByApiKeyId) {
		return new CampaignImport(campaign, project, utmTemplate, totalRows, idempotencyKey, idempotencyRequestHash,
				createdBy, createdByApiKeyId);
	}

	public boolean tryAcquireLease(String owner, Instant now, Instant expiresAt, int maxAttempts) {
		if (status == ImportStatus.COMPLETED || status == ImportStatus.CANCELLED) return false;
		if (status == ImportStatus.PROCESSING && leaseExpiresAt != null && leaseExpiresAt.isAfter(now)) return false;
		if (status == ImportStatus.PROCESSING && attemptCount >= maxAttempts) {
			this.status = ImportStatus.FAILED;
			this.completedAt = now;
			return false;
		}
		this.status = ImportStatus.PROCESSING;
		this.leaseOwner = owner;
		this.leaseExpiresAt = expiresAt;
		this.attemptCount = attemptCount + 1;
		return true;
	}

	public void recordRowResult(boolean succeeded) {
		this.processedRows = processedRows + 1;
		if (succeeded) this.succeededRows = succeededRows + 1;
		else this.failedRows = failedRows + 1;
	}

	public void complete(Instant now) {
		this.status = ImportStatus.COMPLETED;
		this.leaseOwner = null;
		this.leaseExpiresAt = null;
		this.completedAt = now;
	}

	public void fail(Instant now) {
		this.status = ImportStatus.FAILED;
		this.leaseOwner = null;
		this.leaseExpiresAt = null;
		this.completedAt = now;
	}

	public void cancel(Instant now) {
		this.status = ImportStatus.CANCELLED;
		this.leaseOwner = null;
		this.leaseExpiresAt = null;
		this.completedAt = now;
	}

	public boolean isActive() {
		return status == ImportStatus.PENDING || status == ImportStatus.PROCESSING;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}

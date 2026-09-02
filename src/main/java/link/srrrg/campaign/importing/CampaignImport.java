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

/**
 * CSV 임포트 작업 한 건의 상태와 진행률. 여러 파드가 이 행 하나를 두고 조율하므로,
 * 여기 담긴 lease와 시도 횟수가 분산 처리의 유일한 조율 수단이다.
 *
 * <p>{@code leaseOwner}와 {@code leaseExpiresAt}은 어느 파드가 언제까지 이 작업을 잡고 있는지 나타낸다.
 * 파드가 죽으면 lease를 반납할 주체가 없으므로, 만료 시각이 지나면 다른 파드가 회수한다.
 * {@code attemptCount}는 그 회수가 무한히 반복되는 것을 끊는다.</p>
 *
 * <p>집계 컬럼은 행 처리 트랜잭션마다 하나씩 증가한다. 같은 작업을 한 파드만 처리하므로 경합이 없다.</p>
 */
@Entity
@Table(name = "campaign_imports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignImport {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false)
	private Campaign campaign;

	// @SoftDelete 엔티티를 가리키는 to-one 연관은 LAZY로 둘 수 없다.
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
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

	/**
	 * 이 작업을 처리할 권리를 얻는다. 얻지 못하면 거짓을 돌려주고 호출자는 다음 기회를 기다린다.
	 *
	 * <p>순서대로 판단한다. 끝난 작업은 다시 잡지 않고, 아직 유효한 lease가 있으면 다른 파드가
	 * 처리 중이므로 물러난다. lease는 만료됐는데 시도 횟수가 상한에 닿았으면 계속 실패하는 작업으로 보고
	 * 여기서 실패로 확정한다. 이 판정이 없으면 죽는 작업을 영원히 다시 집는다.</p>
	 *
	 * @param owner 이 파드의 식별자. 누가 잡고 있는지 기록용이며 회수 판정에는 쓰지 않는다
	 * @param expiresAt lease 만료 시각. 이 시각이 지나면 다른 파드가 가져갈 수 있다
	 * @return 선점에 성공했는지 여부. 성공하면 상태가 PROCESSING으로 바뀌고 시도 횟수가 하나 늘어난다
	 */
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

	/**
	 * 완료로 확정하면서 lease를 비운다. lease를 남겨 두면 이미 끝난 작업이 잡혀 있는 것처럼 보인다.
	 */
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

	/**
	 * 아직 끝나지 않은 작업인지. 프로젝트당 동시에 하나만 허용하는 제약의 판단 기준이다.
	 */
	public boolean isActive() {
		return status == ImportStatus.PENDING || status == ImportStatus.PROCESSING;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}

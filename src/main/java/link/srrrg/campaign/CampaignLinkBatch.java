package link.srrrg.campaign;

import java.time.Instant;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_link_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignLinkBatch {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false)
	private Campaign campaign;

	@Column(name = "api_key_id", nullable = false)
	private Long apiKeyId;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private CampaignLinkBatch(Campaign campaign, Long apiKeyId, String idempotencyKey, String requestHash) {
		this.campaign = campaign;
		this.apiKeyId = apiKeyId;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
	}

	public static CampaignLinkBatch create(Campaign campaign, Long apiKeyId, String idempotencyKey, String requestHash) {
		return new CampaignLinkBatch(campaign, apiKeyId, idempotencyKey, requestHash);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}

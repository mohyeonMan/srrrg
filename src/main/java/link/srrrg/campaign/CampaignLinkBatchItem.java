package link.srrrg.campaign;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_link_batch_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignLinkBatchItem {
	@EmbeddedId
	private CampaignLinkBatchItemId id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("batchId")
	@JoinColumn(name = "batch_id")
	private CampaignLinkBatch batch;

	@Column(name = "link_id", nullable = false)
	private Long linkId;

	private CampaignLinkBatchItem(CampaignLinkBatch batch, int itemIndex, Long linkId) {
		this.id = new CampaignLinkBatchItemId(batch.getId(), itemIndex);
		this.batch = batch;
		this.linkId = linkId;
	}

	public static CampaignLinkBatchItem create(CampaignLinkBatch batch, int itemIndex, Long linkId) {
		return new CampaignLinkBatchItem(batch, itemIndex, linkId);
	}

	@Embeddable
	public record CampaignLinkBatchItemId(Long batchId, Integer itemIndex) implements Serializable { }
}

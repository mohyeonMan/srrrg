package link.srrrg.campaign;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_utm_defaults")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignUtmDefault {
	@EmbeddedId
	private CampaignUtmDefaultId id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("campaignId")
	@JoinColumn(name = "campaign_id")
	private Campaign campaign;

	@Column(name = "default_value", nullable = false, length = 500)
	private String defaultValue;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private CampaignUtmDefault(Campaign campaign, String fieldName, String defaultValue) {
		this.id = new CampaignUtmDefaultId(campaign.getId(), fieldName);
		this.campaign = campaign;
		this.defaultValue = defaultValue;
	}

	public static CampaignUtmDefault create(Campaign campaign, String fieldName, String defaultValue) {
		return new CampaignUtmDefault(campaign, fieldName, defaultValue);
	}

	public String getFieldName() {
		return id.fieldName();
	}

	public void updateValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = Instant.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	@Embeddable
	public record CampaignUtmDefaultId(Long campaignId, String fieldName) implements Serializable { }
}

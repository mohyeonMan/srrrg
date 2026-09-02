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

/**
 * 캠페인 단위의 UTM 기본값. 링크가 자기 값을 갖지 않은 필드는 리다이렉트 시점에 이 값을 따라간다.
 *
 * <p>링크 생성 시점에 복사하지 않고 참조하는 구조라, 여기 값을 바꾸면 이미 만들어진 링크의
 * 최종 URL이 함께 바뀐다. 필드 이름을 복합 키에 포함해 한 캠페인에 같은 필드의 기본값이
 * 둘 존재할 수 없게 한다.</p>
 */
@Entity
@Table(name = "campaign_utm_defaults")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignUtmDefault {
	@EmbeddedId
	private CampaignUtmDefaultId id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
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

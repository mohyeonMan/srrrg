package link.srrrg.campaign.importing;

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

/**
 * 임포트 행에 지정된 UTM 값. 링크가 만들어지기 전 단계의 값이라 {@code LinkUtmValue}와 별도로 둔다.
 * 링크 생성이 성공하면 이 값들이 링크별 UTM 값으로 옮겨진다.
 */
@Entity
@Table(name = "campaign_import_row_utm_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignImportRowUtmValue {
	@EmbeddedId
	private CampaignImportRowUtmValueId id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("importRowId")
	@JoinColumn(name = "import_row_id")
	private CampaignImportRow importRow;

	@Column(nullable = false, length = 500)
	private String value;

	private CampaignImportRowUtmValue(CampaignImportRow importRow, String fieldName, String value) {
		this.id = new CampaignImportRowUtmValueId(importRow.getId(), fieldName);
		this.importRow = importRow;
		this.value = value;
	}

	public static CampaignImportRowUtmValue create(CampaignImportRow importRow, String fieldName, String value) {
		return new CampaignImportRowUtmValue(importRow, fieldName, value);
	}

	public String getFieldName() {
		return id.fieldName();
	}

	@Embeddable
	public record CampaignImportRowUtmValueId(Long importRowId, String fieldName) implements Serializable { }
}

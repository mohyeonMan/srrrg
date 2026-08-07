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
import link.srrrg.campaign.UtmTemplateField;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("utmTemplateFieldId")
	@JoinColumn(name = "utm_template_field_id")
	private UtmTemplateField field;

	@Column(name = "utm_template_id", nullable = false)
	private Long utmTemplateId;

	@Column(nullable = false, length = 500)
	private String value;

	private CampaignImportRowUtmValue(CampaignImportRow importRow, UtmTemplateField field, Long utmTemplateId, String value) {
		this.id = new CampaignImportRowUtmValueId(importRow.getId(), field.getId());
		this.importRow = importRow;
		this.field = field;
		this.utmTemplateId = utmTemplateId;
		this.value = value;
	}

	public static CampaignImportRowUtmValue create(CampaignImportRow importRow, UtmTemplateField field, Long utmTemplateId, String value) {
		return new CampaignImportRowUtmValue(importRow, field, utmTemplateId, value);
	}

	@Embeddable
	public record CampaignImportRowUtmValueId(Long importRowId, Long utmTemplateFieldId) implements Serializable { }
}

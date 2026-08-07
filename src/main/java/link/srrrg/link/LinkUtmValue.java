package link.srrrg.link;

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
import jakarta.persistence.Table;
import link.srrrg.campaign.UtmTemplateField;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_utm_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkUtmValue {
	@EmbeddedId
	private LinkUtmValueId id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("linkId")
	@JoinColumn(name = "link_id")
	private Link link;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("utmTemplateFieldId")
	@JoinColumn(name = "utm_template_field_id")
	private UtmTemplateField field;

	@Column(name = "utm_template_id", nullable = false)
	private Long utmTemplateId;

	@Column(nullable = false, length = 500)
	private String value;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private LinkUtmValue(Link link, UtmTemplateField field, Long utmTemplateId, String value) {
		this.id = new LinkUtmValueId(link.getId(), field.getId());
		this.link = link;
		this.field = field;
		this.utmTemplateId = utmTemplateId;
		this.value = value;
	}

	public static LinkUtmValue create(Link link, UtmTemplateField field, Long utmTemplateId, String value) {
		return new LinkUtmValue(link, field, utmTemplateId, value);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	@Embeddable
	public record LinkUtmValueId(Long linkId, Long utmTemplateFieldId) implements Serializable { }
}

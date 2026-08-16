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

	// @SoftDelete 엔티티를 가리키는 to-one 연관은 LAZY로 둘 수 없다.
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@MapsId("linkId")
	@JoinColumn(name = "link_id")
	private Link link;

	@Column(nullable = false, length = 500)
	private String value;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private LinkUtmValue(Link link, String fieldName, String value) {
		this.id = new LinkUtmValueId(link.getId(), fieldName);
		this.link = link;
		this.value = value;
	}

	public static LinkUtmValue create(Link link, String fieldName, String value) {
		return new LinkUtmValue(link, fieldName, value);
	}

	public String getFieldName() {
		return id.fieldName();
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	@Embeddable
	public record LinkUtmValueId(Long linkId, String fieldName) implements Serializable { }
}

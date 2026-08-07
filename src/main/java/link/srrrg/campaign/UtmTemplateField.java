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
@Table(name = "utm_template_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UtmTemplateField {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "utm_template_id", nullable = false)
	private UtmTemplate utmTemplate;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private UtmTemplateField(UtmTemplate utmTemplate, String name) {
		this.utmTemplate = utmTemplate;
		this.name = name;
	}

	public static UtmTemplateField create(UtmTemplate utmTemplate, String name) {
		return new UtmTemplateField(utmTemplate, name);
	}

	public void delete() {
		this.deletedAt = Instant.now();
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}

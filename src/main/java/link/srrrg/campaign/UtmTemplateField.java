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

/**
 * 템플릿에 속한 UTM 필드 하나. 이 이름이 그대로 리다이렉트 URL의 쿼리 파라미터 이름이 된다.
 *
 * <p>삭제도 {@code deletedAt}으로만 표시한다. 필드를 지우면 그 UTM은 이후 리다이렉트부터 사라지지만,
 * 이미 저장된 링크별 값과 캠페인 기본값은 남아 있어 필드를 되살리면 다시 적용된다.</p>
 */
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

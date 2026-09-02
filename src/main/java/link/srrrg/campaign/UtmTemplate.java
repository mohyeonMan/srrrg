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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import link.srrrg.project.Project;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캠페인이 쓸 UTM 필드 집합의 정의. 여러 캠페인이 같은 템플릿을 공유할 수 있어,
 * 필드를 바꾸면 그 템플릿을 쓰는 모든 캠페인의 링크에 영향이 간다.
 *
 * <p>{@code @SoftDelete} 대신 {@code deletedAt}을 직접 두는 것은 삭제된 템플릿도 조회할 수 있어야 하기
 * 때문이다. 이미 그 템플릿으로 만들어진 링크가 남아 있으므로 조회 자체를 막으면 안 된다.
 * 대신 사용 가능 여부는 {@link #isDeleted()}로 호출부가 판단한다.</p>
 */
@Entity
@Table(name = "utm_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UtmTemplate {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// @SoftDelete 엔티티를 가리키는 to-one 연관은 LAZY로 둘 수 없다.
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private UtmTemplate(Project project, String name) {
		this.project = project;
		this.name = name;
	}

	public static UtmTemplate create(Project project, String name) {
		return new UtmTemplate(project, name);
	}

	public void rename(String name) {
		this.name = name;
	}

	public void delete() {
		this.deletedAt = Instant.now();
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = Instant.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}

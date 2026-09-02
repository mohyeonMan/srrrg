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
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import link.srrrg.identity.User;
import link.srrrg.project.Project;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 링크를 묶는 캠페인이자 UTM 값의 상속 원천.
 *
 * <p>{@code defaultOriginalUrl}과 캠페인 UTM 기본값은 복사되지 않고 리다이렉트 시점에 참조된다.
 * 그래서 여기서 목적지를 바꾸면 자체 목적지가 없는 기존 링크의 이동 대상이 한꺼번에 바뀐다.
 * 대량 발행한 링크의 목적지를 나중에 일괄 변경할 수 있게 하려는 설계다.</p>
 *
 * <p>{@code utmTemplate}이 어떤 UTM 필드를 쓸지 정한다. 템플릿이 없으면 UTM 값을 아예 받을 수 없다.</p>
 *
 * <p>{@code @SoftDelete}라 삭제해도 행이 남는다. 삭제 시 소속 링크와 진행 중인 import를 함께 정리하는
 * 책임은 {@code CampaignService.delete}에 있다.</p>
 */
@Entity
@Table(name = "campaigns")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// @SoftDelete 엔티티를 가리키는 to-one 연관은 LAZY로 둘 수 없다.
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "utm_template_id")
	private UtmTemplate utmTemplate;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 500)
	private String description;

	@Column(name = "default_original_url", length = 2048)
	private String defaultOriginalUrl;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id")
	private User createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	private Campaign(Project project, String name, String description, String defaultOriginalUrl, User createdBy) {
		this.project = project;
		this.name = name;
		this.description = description;
		this.defaultOriginalUrl = defaultOriginalUrl;
		this.createdBy = createdBy;
	}

	public static Campaign create(Project project, String name, String description, String defaultOriginalUrl, User createdBy) {
		return new Campaign(project, name, description, defaultOriginalUrl, createdBy);
	}

	public void rename(String name) {
		this.name = name;
	}

	public void changeDescription(String description) {
		this.description = description;
	}

	/**
	 * 기본 목적지를 바꾼다. 자체 목적지가 없는 소속 링크의 이동 대상이 즉시 함께 바뀐다.
	 */
	public void changeDefaultOriginalUrl(String defaultOriginalUrl) {
		this.defaultOriginalUrl = defaultOriginalUrl;
	}

	/**
	 * 사용할 UTM 템플릿을 지정한다. 같은 프로젝트의 템플릿인지 확인하는 책임은 호출자에 있다.
	 * 템플릿이 바뀌면 새 템플릿에 없는 필드의 값은 유효값 계산에서 제외되어 링크 URL에서 사라진다.
	 */
	public void selectTemplate(UtmTemplate utmTemplate) {
		this.utmTemplate = utmTemplate;
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

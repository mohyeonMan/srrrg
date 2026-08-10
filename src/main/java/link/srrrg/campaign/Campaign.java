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
import link.srrrg.identity.User;
import link.srrrg.project.Project;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaigns")
@SoftDelete(columnName = "is_deleted")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
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

	public void changeDefaultOriginalUrl(String defaultOriginalUrl) {
		this.defaultOriginalUrl = defaultOriginalUrl;
	}

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

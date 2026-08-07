package link.srrrg.domain;

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
import link.srrrg.project.Project;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_domains")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectDomain {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false, unique = true)
	private Project project;

	@Column(nullable = false, unique = true, length = 253)
	private String hostname;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private ProjectDomain(Project project, String hostname) {
		this.project = project;
		this.hostname = hostname;
	}

	public static ProjectDomain create(Project project, String hostname) {
		return new ProjectDomain(project, hostname);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}

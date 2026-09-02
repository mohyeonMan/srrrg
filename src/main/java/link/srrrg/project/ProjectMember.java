package link.srrrg.project;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import link.srrrg.identity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자와 프로젝트의 소속 관계이자 권한 판정의 근거. 이 행이 없으면 프로젝트 자원에 접근할 수 없다.
 * 복합 키를 쓰므로 한 사용자가 같은 프로젝트에 두 역할로 존재할 수 없다.
 */
@Entity
@Table(name = "project_members")
@Getter
@NoArgsConstructor
public class ProjectMember {
	@EmbeddedId
	private ProjectMemberId id;
	@ManyToOne
	@MapsId("projectId")
	@JoinColumn(name = "project_id")
	private Project project;
	@ManyToOne
	@MapsId("userId")
	@JoinColumn(name = "user_id")
	private User user;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ProjectRole role;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public ProjectMember(Project project, User user, ProjectRole role) {
		this.id = new ProjectMemberId(project.getId(), user.getId());
		this.project = project;
		this.user = user;
		this.role = role;
		this.createdAt = Instant.now();
	}

	public void changeRole(ProjectRole role) {
		this.role = role;
	}

	@Embeddable
	public record ProjectMemberId(Long projectId, Long userId) implements Serializable {
	}
}

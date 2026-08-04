package link.srrrg.project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectInvitation {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne @JoinColumn(name = "project_id", nullable = false) private Project project;
	@Column(nullable = false, length = 320) private String email;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ProjectRole role;
	@Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
	@Column(name = "expires_at", nullable = false) private Instant expiresAt;
	@Column(name = "cancelled_at") private Instant cancelledAt;
	@Column(name = "accepted_at") private Instant acceptedAt;
	@Column(name = "created_at", nullable = false) private Instant createdAt;

	private ProjectInvitation(Project project, String email, ProjectRole role, String tokenHash, Instant expiresAt) {
		this.project = project; this.email = email; this.role = role; this.tokenHash = tokenHash; this.expiresAt = expiresAt; this.createdAt = Instant.now();
	}
	public static ProjectInvitation create(Project project, String email, ProjectRole role, String tokenHash, Instant expiresAt) { return new ProjectInvitation(project, email, role, tokenHash, expiresAt); }
	public boolean isUsable(Instant now) { return cancelledAt == null && acceptedAt == null && expiresAt.isAfter(now); }
	public void cancel() { cancelledAt = Instant.now(); }
	public void accept() { acceptedAt = Instant.now(); }
}

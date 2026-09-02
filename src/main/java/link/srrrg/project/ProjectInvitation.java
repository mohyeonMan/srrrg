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

/**
 * 프로젝트 초대 한 건. 토큰 원문은 메일에만 있고 여기에는 해시만 남으므로,
 * 초대 링크를 잃어버리면 다시 보내는 것 외에 복구할 방법이 없다.
 *
 * <p>취소·수락·만료가 각각 다른 컬럼이다. 세 상태를 구분해야 이미 처리된 초대를 다시 쓰려는 시도와
 * 단순 만료를 나눌 수 있다.</p>
 */
@Entity
@Table(name = "project_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectInvitation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;
	@Column(nullable = false, length = 320)
	private String email;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ProjectRole role;
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	@Column(name = "cancelled_at")
	private Instant cancelledAt;
	@Column(name = "accepted_at")
	private Instant acceptedAt;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private ProjectInvitation(Project project, String email, ProjectRole role, String tokenHash, Instant expiresAt) {
		this.project = project;
		this.email = email;
		this.role = role;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public static ProjectInvitation create(Project project, String email, ProjectRole role, String tokenHash,
			Instant expiresAt) {
		return new ProjectInvitation(project, email, role, tokenHash, expiresAt);
	}

	/**
	 * 아직 쓸 수 있는 초대인지 판정한다. 취소·수락·만료 중 하나라도 해당하면 쓸 수 없다.
	 * 수락된 초대를 다시 쓸 수 없게 하는 것이 링크 재사용을 막는 지점이다.
	 */
	public boolean isUsable(Instant now) {
		return cancelledAt == null && acceptedAt == null && expiresAt.isAfter(now);
	}

	public void cancel() {
		cancelledAt = Instant.now();
	}

	public void accept() {
		acceptedAt = Instant.now();
	}
}

package link.srrrg.identity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이 서비스의 사용자 계정. 비밀번호가 없고 공급자 로그인으로만 만들어지므로,
 * 이 엔티티에는 자격증명이 전혀 담기지 않는다.
 *
 * <p>이메일은 계정 연결 판단에 쓰이기 때문에 공급자가 검증한 주소만 저장한다.
 * 검증되지 않은 값이 여기 들어오면 그것만으로 기존 계정과 이어질 수 있다.</p>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** OAuth 공급자가 검증한 주소만 담는다. 공급자가 이메일을 주지 않으면 null. */
	@Column(length = 320)
	private String email;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "onboarding_completed_at")
	private Instant onboardingCompletedAt;

	private User(String email, String displayName) {
		this.email = email;
		this.displayName = displayName;
	}

	public static User create(String email, String displayName) {
		return new User(email, displayName);
	}

	public void updateDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void completeOnboarding(String displayName) {
		this.displayName = displayName;
		this.onboardingCompletedAt = Instant.now();
	}

	/**
	 * 온보딩 완료 시각이 비어 있으면 아직 마치지 않은 것으로 본다.
	 * 로그인 성공 처리기가 이 값으로 첫 목적지를 정한다.
	 */
	public boolean needsOnboarding() {
		return onboardingCompletedAt == null;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}

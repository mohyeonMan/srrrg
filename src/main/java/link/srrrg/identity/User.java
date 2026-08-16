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

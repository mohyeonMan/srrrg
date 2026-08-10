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

	@Column(length = 320)
	private String email;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

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
		this.emailVerifiedAt = email == null ? null : Instant.now();
		this.displayName = displayName;
	}

	public static User create(String email, String displayName) {
		return new User(email, displayName);
	}

	public void updateDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void completeOnboarding(String displayName, String email) {
		this.displayName = displayName;
		if (this.email == null) {
			this.email = email;
		}
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

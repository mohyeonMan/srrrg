package link.srrrg.project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import link.srrrg.identity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "projects")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(unique = true, length = 63)
	private String subdomain;

	@Column(name = "subdomain_enabled", nullable = false)
	private boolean subdomainEnabled;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id")
	private User createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private Project(String name, String subdomain, User createdBy) {
		this.name = name;
		this.subdomain = subdomain;
		this.subdomainEnabled = false;
		this.createdBy = createdBy;
	}
	public static Project create(String name, String subdomain, User createdBy) { return new Project(name, subdomain, createdBy); }

	public void rename(String name) { this.name = name; }
	public void claimSubdomain(String subdomain) { this.subdomain = subdomain; }
	public void releaseSubdomain() { this.subdomain = null; this.subdomainEnabled = false; }
	public void setSubdomainEnabled(boolean enabled) {
		if (enabled && subdomain == null) throw new IllegalStateException("선점한 서브도메인이 없습니다.");
		this.subdomainEnabled = enabled;
	}
	public String activeSubdomain() { return subdomainEnabled ? subdomain : null; }

	@PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
	@PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}

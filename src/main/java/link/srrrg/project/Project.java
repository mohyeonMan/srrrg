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

/**
 * 링크와 캠페인을 담는 작업 공간이자 권한의 경계. 모든 프로젝트 자원 접근은 이 프로젝트에 대한
 * 멤버십으로 판정된다.
 *
 * <p>서브도메인은 선점({@code subdomain})과 활성화({@code subdomainEnabled})가 분리돼 있다.
 * 선점만으로는 링크가 그 호스트로 발급되지 않으며, 활성화된 뒤 만들어진 링크만 서브도메인 코드 공간에 들어간다.
 * 나중에 활성화를 끄면 이미 그 공간에 발급된 링크는 접근 경로를 잃는다.</p>
 *
 * <p>{@code @SoftDelete}라 삭제해도 행이 남는다. 링크와 멤버십 조회가 모두 이 엔티티를 조인하므로
 * 한 행만 지워도 소속 자원 전체가 조회에서 빠진다.</p>
 */
@Entity
@Table(name = "projects")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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

	public static Project create(String name, String subdomain, User createdBy) {
		return new Project(name, subdomain, createdBy);
	}

	public void rename(String name) {
		this.name = name;
	}

	public void claimSubdomain(String subdomain) {
		this.subdomain = subdomain;
	}

	public void releaseSubdomain() {
		this.subdomain = null;
		this.subdomainEnabled = false;
	}

	/**
	 * 서브도메인 사용을 켜고 끈다. 선점한 값이 없으면 켤 수 없다.
	 * 허용하면 호스트가 정해지지 않은 채 활성 상태가 되어 링크 발급이 어긋난다.
	 */
	public void setSubdomainEnabled(boolean enabled) {
		if (enabled && subdomain == null)
			throw new IllegalStateException("선점한 서브도메인이 없습니다.");
		this.subdomainEnabled = enabled;
	}

	/**
	 * 지금 링크를 발급할 코드 공간을 돌려준다. 활성화되지 않았으면 {@code null}이며,
	 * 그 값이 곧 베이스 도메인 공간을 뜻한다. 링크 생성 경로는 선점 값이 아니라 반드시 이 값을 써야
	 * 비활성 상태에서 열리지 않는 주소가 만들어지지 않는다.
	 */
	public String activeSubdomain() {
		return subdomainEnabled ? subdomain : null;
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

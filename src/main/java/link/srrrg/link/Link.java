package link.srrrg.link;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import link.srrrg.campaign.Campaign;
import link.srrrg.campaign.UtmTemplate;
import link.srrrg.project.Project;
import link.srrrg.identity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단축 링크 한 건. 익명 링크와 프로젝트·캠페인 링크가 같은 표를 쓰며, 어떤 종류인지는 채워진 컬럼으로 갈린다.
 *
 * <p>익명 링크는 {@code secretKeyHash}가 있고 프로젝트가 없다. 프로젝트 링크는 반대이며,
 * 캠페인 링크는 거기에 campaign과 utmTemplate이 더 붙는다. 이 구분은 리다이렉트에서
 * 위험 검사 여부와 목적지 계산 방식을 가르므로 {@link #isAnonymous()}로만 판단한다.</p>
 *
 * <p>단축 코드는 {@code subdomain}과 함께여야 유일하다. 베이스 도메인과 프로젝트 서브도메인은
 * 서로 다른 코드 공간이라 같은 코드가 다른 링크를 가리킬 수 있다.</p>
 *
 * <p>{@code @SoftDelete}가 걸려 있어 삭제는 {@code deleted_at}을 찍는 UPDATE로 번역되고,
 * 이후 모든 조회에서 자동으로 빠진다. 통계 이벤트가 링크를 참조하므로 물리 삭제를 하지 않는다.</p>
 *
 * <p>{@code originalUrl}이 비어 있을 수 있다. 캠페인 링크는 목적지를 캠페인 기본값에서 상속받기 때문이며,
 * 둘 다 없으면 리다이렉트가 410으로 끝난다.</p>
 */
@Entity
@Table(name = "links")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Link {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 6)
	private String code;

	@Column(name = "original_url", length = 2048)
	private String originalUrl;

	@Column(length = 100)
	private String name;

	@Column(name = "secret_key_hash", length = 100)
	private String secretKeyHash;

	// @SoftDelete 엔티티를 가리키는 to-one 연관은 LAZY로 둘 수 없다(Hibernate가 부팅 시 거부).
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "project_id")
	private Project project;

	@Column(length = 63)
	private String subdomain;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id")
	private User createdBy;

	// API key 요청의 멱등성 판정에 쓰는 세 값. 같은 키로 같은 요청이 다시 오면 새로 만들지 않고 기존 링크를 돌려주고,
	// 같은 키에 다른 요청 내용이 오면 충돌로 거부한다. 요청 해시가 그 판단 근거다.
	@Column(name = "idempotency_api_key_id")
	private Long idempotencyApiKeyId;
	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;
	@Column(name = "idempotency_request_hash", length = 64)
	private String idempotencyRequestHash;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "campaign_id")
	private Campaign campaign;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "utm_template_id")
	private UtmTemplate utmTemplate;

	@Column(name = "external_id", length = 100)
	private String externalId;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private Link(String code, String originalUrl, String secretKeyHash, Instant expiresAt) {
		this.code = code;
		this.originalUrl = originalUrl;
		this.secretKeyHash = secretKeyHash;
		this.expiresAt = expiresAt;
	}

	public static Link create(String code, String originalUrl, String secretKeyHash, Instant expiresAt) {
		return new Link(code, originalUrl, secretKeyHash, expiresAt);
	}

	public static Link createForProject(String code, String originalUrl, Instant expiresAt,
			Project project, String subdomain, User createdBy, Long apiKeyId, String idempotencyKey, String requestHash) {
		return createForProject(code, originalUrl, expiresAt, project, subdomain, createdBy, apiKeyId, idempotencyKey, requestHash, null);
	}

	public static Link createForProject(String code, String originalUrl, Instant expiresAt,
			Project project, String subdomain, User createdBy, Long apiKeyId, String idempotencyKey, String requestHash, String name) {
		return createForCampaign(code, originalUrl, expiresAt, project, subdomain, createdBy, apiKeyId, idempotencyKey, requestHash,
				null, null, null, name);
	}

	public static Link createForCampaign(String code, String originalUrl, Instant expiresAt,
			Project project, String subdomain, User createdBy, Long apiKeyId, String idempotencyKey, String requestHash,
			Campaign campaign, UtmTemplate utmTemplate, String externalId) {
		return createForCampaign(code, originalUrl, expiresAt, project, subdomain, createdBy, apiKeyId, idempotencyKey, requestHash,
				campaign, utmTemplate, externalId, null);
	}

	public static Link createForCampaign(String code, String originalUrl, Instant expiresAt,
			Project project, String subdomain, User createdBy, Long apiKeyId, String idempotencyKey, String requestHash,
			Campaign campaign, UtmTemplate utmTemplate, String externalId, String name) {
		Link link = new Link(code, originalUrl, null, expiresAt);
		link.name = name;
		link.project = project;
		link.subdomain = subdomain;
		link.createdBy = createdBy;
		link.idempotencyApiKeyId = apiKeyId;
		link.idempotencyKey = idempotencyKey;
		link.idempotencyRequestHash = requestHash;
		link.campaign = campaign;
		link.utmTemplate = utmTemplate;
		link.externalId = externalId;
		return link;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	/**
	 * 만료 시각이 없으면 영구 링크다. 시각이 정확히 같은 순간도 만료로 본다.
	 * 한 요청 안에서 판정과 기록이 같은 시각을 쓰도록 호출자가 현재 시각을 넘긴다.
	 */
	public boolean isExpiredAt(Instant instant) {
		return expiresAt != null && !expiresAt.isAfter(instant);
	}

	public void updateOriginalUrl(String originalUrl) {
		this.originalUrl = originalUrl;
		this.updatedAt = Instant.now();
	}

	public void updateExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
		this.updatedAt = Instant.now();
	}

	/**
	 * 익명 링크인지 판별한다. secret key는 익명 링크에만 발급되고
	 * {@link #assignToProject}로 프로젝트에 편입되면 지워지므로 이것이 정확한 기준이다.
	 * project가 null인지로 판별하면 프로젝트가 삭제돼 연관이 비었을 때 익명 링크로 오인한다.
	 */
	public boolean isAnonymous() {
		return secretKeyHash != null;
	}

	/**
	 * 익명 링크를 프로젝트로 편입한다. secret key 해시를 지우는 것이 핵심이다.
	 * 남겨 두면 프로젝트 소유가 된 뒤에도 예전 secret key를 가진 사람이 계속 수정·삭제할 수 있다.
	 * 이 시점부터 {@link #isAnonymous()}가 거짓이 되어 리다이렉트의 위험 검사 대상에서도 빠진다.
	 */
	public void assignToProject(Project project, User createdBy) {
		this.project = project;
		this.createdBy = createdBy;
		this.secretKeyHash = null;
		this.updatedAt = Instant.now();
	}
}

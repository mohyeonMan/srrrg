package link.srrrg.link.access;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import link.srrrg.link.Link;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_access_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkAccessEvent {

	public enum Outcome {
		REDIRECTED,
		BLOCKED,
		CHECK_FAILED,
		URL_CHANGED,
		EXPIRED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// @SoftDelete 엔티티를 가리키는 to-one 연관은 LAZY로 둘 수 없다.
	// 이 엔티티는 적재 전용이고 통계는 네이티브 SQL로 읽으므로 조회 비용에 영향이 없다.
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "link_id", nullable = false)
	private Link link;

	@Column(name = "accessed_at", nullable = false)
	private Instant accessedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Outcome outcome;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(columnDefinition = "TEXT")
	private String referer;

	@Column(name = "user_agent", columnDefinition = "TEXT")
	private String userAgent;

	@Column(name = "browser_name", length = 50)
	private String browserName;

	@Column(name = "browser_version", length = 50)
	private String browserVersion;

	@Column(name = "os_name", length = 50)
	private String osName;

	@Column(name = "os_version", length = 50)
	private String osVersion;

	@Column(name = "device_type", length = 30)
	private String deviceType;

	@Column(name = "is_bot", nullable = false)
	private boolean bot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "effective_utm", nullable = false, columnDefinition = "jsonb")
	private Map<String, String> effectiveUtm;

	private LinkAccessEvent(Link link, Instant accessedAt, Outcome outcome,
			ClientRequestInfo requestInfo, UserAgentInfo userAgentInfo, Map<String, String> effectiveUtm) {
		this.link = link;
		this.accessedAt = accessedAt;
		this.outcome = outcome;
		this.ipAddress = requestInfo.ipAddress();
		this.referer = requestInfo.referer();
		this.userAgent = requestInfo.userAgent();
		this.browserName = userAgentInfo.browserName();
		this.browserVersion = userAgentInfo.browserVersion();
		this.osName = userAgentInfo.osName();
		this.osVersion = userAgentInfo.osVersion();
		this.deviceType = userAgentInfo.deviceType();
		this.bot = userAgentInfo.bot();
		this.effectiveUtm = Map.copyOf(effectiveUtm);
	}

	public static LinkAccessEvent create(Link link, Instant accessedAt, Outcome outcome,
			ClientRequestInfo requestInfo, UserAgentInfo userAgentInfo, Map<String, String> effectiveUtm) {
		return new LinkAccessEvent(link, accessedAt, outcome, requestInfo, userAgentInfo, effectiveUtm);
	}
}

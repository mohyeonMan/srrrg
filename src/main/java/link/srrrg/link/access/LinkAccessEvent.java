package link.srrrg.link.access;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import link.srrrg.link.Link;

@Entity
@Table(name = "link_access_events")
public class LinkAccessEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "link_id", nullable = false)
	private Link link;

	@Column(name = "accessed_at", nullable = false)
	private Instant accessedAt;

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

	protected LinkAccessEvent() {
	}

	private LinkAccessEvent(
			Link link,
			ClientRequestInfo requestInfo,
			UserAgentInfo userAgentInfo
	) {
		this.link = link;
		this.ipAddress = requestInfo.ipAddress();
		this.referer = requestInfo.referer();
		this.userAgent = requestInfo.userAgent();
		this.browserName = userAgentInfo.browserName();
		this.browserVersion = userAgentInfo.browserVersion();
		this.osName = userAgentInfo.osName();
		this.osVersion = userAgentInfo.osVersion();
		this.deviceType = userAgentInfo.deviceType();
		this.bot = userAgentInfo.bot();
	}

	public static LinkAccessEvent create(
			Link link,
			ClientRequestInfo requestInfo,
			UserAgentInfo userAgentInfo
	) {
		return new LinkAccessEvent(link, requestInfo, userAgentInfo);
	}

	@PrePersist
	void onCreate() {
		accessedAt = Instant.now();
	}
}

package link.srrrg.link.management.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

@Getter
public class UpdateLinkRequest {

	private String originalUrl;
	private Instant expiresAt;
	private boolean originalUrlPresent;
	private boolean expiresAtPresent;

	@JsonSetter("originalUrl")
	public void setOriginalUrl(String originalUrl) {
		this.originalUrl = originalUrl;
		this.originalUrlPresent = true;
	}

	@JsonSetter("expiresAt")
	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
		this.expiresAtPresent = true;
	}

	public boolean hasChanges() {
		return originalUrlPresent || expiresAtPresent;
	}
}

package link.srrrg.campaign.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

@Getter
public class UpdateCampaignRequest {

	private String name;
	private String description;
	private String defaultOriginalUrl;
	private boolean namePresent;
	private boolean descriptionPresent;
	private boolean defaultOriginalUrlPresent;

	@JsonSetter("name")
	public void setName(String name) {
		this.name = name;
		this.namePresent = true;
	}

	@JsonSetter("description")
	public void setDescription(String description) {
		this.description = description;
		this.descriptionPresent = true;
	}

	@JsonSetter("defaultOriginalUrl")
	public void setDefaultOriginalUrl(String defaultOriginalUrl) {
		this.defaultOriginalUrl = defaultOriginalUrl;
		this.defaultOriginalUrlPresent = true;
	}

	public boolean hasChanges() {
		return namePresent || descriptionPresent || defaultOriginalUrlPresent;
	}
}

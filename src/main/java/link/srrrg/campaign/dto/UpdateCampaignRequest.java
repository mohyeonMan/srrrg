package link.srrrg.campaign.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

@Getter
public class UpdateCampaignRequest {

	private String name;
	private String description;
	private boolean namePresent;
	private boolean descriptionPresent;

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

	public boolean hasChanges() {
		return namePresent || descriptionPresent;
	}
}

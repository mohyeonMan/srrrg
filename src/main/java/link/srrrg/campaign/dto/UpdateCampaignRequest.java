package link.srrrg.campaign.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

@Getter
/**
 * 캠페인 부분 수정 요청. record가 아니라 setter를 둔 이유는 값을 보내지 않은 것과 {@code null}로
 * 보낸 것을 구분해야 하기 때문이다.
 *
 * <p>Jackson은 두 경우 모두 필드를 {@code null}로 두므로, setter 호출 여부를 플래그로 기록해
 * 요청에 포함된 필드만 반영한다. 이 구분이 없으면 이름만 바꾸려는 요청이 설명과 기본 목적지까지 비운다.</p>
 *
 * <p>기본 목적지를 {@code null}로 보내는 것은 실제로 의미가 있다. 목적지 상속을 없애겠다는 뜻이며,
 * 그러면 자체 목적지가 없는 소속 링크는 리다이렉트에서 410이 된다.</p>
 */
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

	/**
	 * 아무 필드도 오지 않은 요청을 걸러낸다. 값 없이 들어온 수정은 아무 일도 하지 않고 성공으로 끝나므로
	 * 호출자가 400으로 되돌린다.
	 */
	public boolean hasChanges() {
		return namePresent || descriptionPresent || defaultOriginalUrlPresent;
	}
}

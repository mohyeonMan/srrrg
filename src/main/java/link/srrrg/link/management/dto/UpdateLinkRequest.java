package link.srrrg.link.management.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

@Getter
/**
 * 부분 수정 요청. record가 아니라 setter를 둔 이유는 값을 보내지 않은 것과 {@code null}로
 * 보낸 것을 구분해야 하기 때문이다.
 *
 * <p>Jackson은 두 경우 모두 필드를 {@code null}로 둔다. 그래서 setter가 호출됐는지를 별도 플래그로
 * 기록해, 요청에 포함된 필드만 반영한다. 이 구분이 없으면 만료 시각만 바꾸려는 요청이
 * 목적지 URL까지 비운다.</p>
 *
 * <p>캠페인 링크에서는 목적지를 {@code null}로 보내는 것이 실제로 의미가 있다.
 * 캠페인 기본 목적지를 다시 상속하겠다는 뜻이다.</p>
 */
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

	/**
	 * 아무 필드도 오지 않은 요청을 걸러낸다. 값 없이 들어온 수정 요청은 아무 일도 하지 않고
	 * 성공으로 끝나므로, 호출자가 400으로 되돌린다.
	 */
	public boolean hasChanges() {
		return originalUrlPresent || expiresAtPresent;
	}
}

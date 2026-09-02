package link.srrrg.project;

/**
 * API 키가 가질 수 있는 권한 단위. 상수 이름이 아니라 {@code value()} 문자열이 DB와 API 응답에 쓰이므로,
 * 상수 이름은 바꿔도 되지만 문자열을 바꾸면 이미 발급된 키의 권한이 해석되지 않는다.
 *
 * <p>읽기와 쓰기를 나눈 것은 연동에 필요한 최소 권한만 주도록 하기 위해서다.</p>
 */
public enum ApiKeyScope {
	LINKS_READ("links:read"),
	LINKS_WRITE("links:write"),
	CAMPAIGNS_READ("campaigns:read"),
	CAMPAIGNS_WRITE("campaigns:write"),
	STATS_READ("stats:read");

	private final String value;
	ApiKeyScope(String value) { this.value = value; }
	public String value() { return value; }
	public static ApiKeyScope fromValue(String value) {
		for (ApiKeyScope scope : values()) if (scope.value.equals(value)) return scope;
		throw new IllegalArgumentException("지원하지 않는 API key scope입니다.");
	}
}

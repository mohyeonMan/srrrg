package link.srrrg.project;

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

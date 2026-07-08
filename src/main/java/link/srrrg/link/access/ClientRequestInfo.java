package link.srrrg.link.access;

public record ClientRequestInfo(
		String ipAddress,
		String referer,
		String userAgent
) {
}

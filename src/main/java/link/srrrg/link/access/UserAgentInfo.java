package link.srrrg.link.access;

public record UserAgentInfo(
		String browserName,
		String browserVersion,
		String osName,
		String osVersion,
		String deviceType,
		boolean bot
) {
}

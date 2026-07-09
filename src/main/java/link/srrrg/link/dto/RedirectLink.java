package link.srrrg.link.dto;

public record RedirectLink(
		String code,
		String originalUrl,
		boolean trusted
) {
}

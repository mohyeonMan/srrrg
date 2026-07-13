package link.srrrg.link.dto;

import link.srrrg.link.LinkStatus;

public record RedirectLink(
		String code,
		String originalUrl,
		LinkStatus cachedStatus
) {
}

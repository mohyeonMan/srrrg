package link.srrrg.link.redirect.dto;

import link.srrrg.link.LinkStatus;

public record RedirectLink(
		String code,
		String originalUrl,
		LinkStatus cachedStatus,
		String cachedRedirectUrl
) {
}

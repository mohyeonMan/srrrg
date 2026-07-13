package link.srrrg.link.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import link.srrrg.link.risk.UrlRiskCheckResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RedirectCheckResponse(
		UrlRiskCheckResult status,
		String redirectUrl
) {
}

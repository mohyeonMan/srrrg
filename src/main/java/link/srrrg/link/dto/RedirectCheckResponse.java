package link.srrrg.link.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import link.srrrg.link.risk.UrlRiskCheckResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RedirectCheckResponse(
		UrlRiskCheckResult status,
		// 외부 원본 URL이 아니라 srrrg가 302를 발급하는 내부 이동 URL임.
		String redirectUrl
) {
}

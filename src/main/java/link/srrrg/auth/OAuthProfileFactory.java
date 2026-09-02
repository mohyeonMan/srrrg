package link.srrrg.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.OAuthProvider;

/**
 * 인증이 끝난 Spring Security 토큰에서 이 서비스가 쓰는 신원 값을 꺼낸다.
 * 공급자 사용자 식별자는 {@code authentication.getName()}이며, 이 값이 공급자 안에서 변하지 않는
 * 유일 키라 계정 매칭의 기준이 된다. 이메일은 바뀔 수 있어 기준으로 쓰지 않는다.
 */
final class OAuthProfileFactory {

	private OAuthProfileFactory() {
	}

	static OAuthIdentity from(OAuth2AuthenticationToken authentication) {
		return new OAuthIdentity(
				OAuthProvider.fromRegistrationId(authentication.getAuthorizedClientRegistrationId()),
				authentication.getName(),
				authentication.getPrincipal().getAttribute(ProviderOAuth2UserService.PROVIDER_EMAIL),
				Boolean.TRUE.equals(authentication.getPrincipal().getAttribute(
						ProviderOAuth2UserService.PROVIDER_EMAIL_VERIFIED)),
				authentication.getPrincipal().getAttribute(ProviderOAuth2UserService.DISPLAY_NAME));
	}
}

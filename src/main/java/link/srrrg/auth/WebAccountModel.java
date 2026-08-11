package link.srrrg.auth;

import java.util.Optional;

import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.identity.UserRepository;

@ControllerAdvice
class WebAccountModel {
	private final UserRepository users;
	private final JwtService jwtService;

	WebAccountModel(UserRepository users, JwtService jwtService) {
		this.users = users;
		this.jwtService = jwtService;
	}

	@ModelAttribute
	void account(@AuthenticationPrincipal SrrrgPrincipal principal, HttpServletRequest request, Model model) {
		sessionUserId(principal, request)
				.flatMap(users::findById)
				.ifPresent(user -> model.addAttribute("accountUser",
						new AccountUser(user.getDisplayName(), user.getEmail())));
		model.addAttribute("accountReturnTo", request.getRequestURI());
	}

	/**
	 * access token 이 만료돼도 refresh token 이 살아 있으면 세션은 유지된다.
	 * 이때 클라이언트는 첫 API 호출에서 조용히 갱신하므로 화면은 정상 동작하는데,
	 * 헤더만 로그아웃으로 남아 로그인 여부를 오해하게 된다.
	 * 서명이 유효한 만료 token 을 표시 용도로만 읽는다. 권한은 부여하지 않는다.
	 */
	private Optional<Long> sessionUserId(SrrrgPrincipal principal, HttpServletRequest request) {
		if (principal != null) {
			return Optional.of(principal.userId());
		}
		String expiredAccessToken = JwtAuthenticationFilter.cookie(request, WebTokenCookies.ACCESS_COOKIE);
		if (expiredAccessToken == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(jwtService.readSubjectAllowingExpired(expiredAccessToken));
		} catch (IllegalArgumentException | IllegalStateException invalidToken) {
			return Optional.empty();
		}
	}

	record AccountUser(String displayName, String email) { }
}

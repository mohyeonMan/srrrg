package link.srrrg.auth;

import java.util.Optional;

import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.identity.UserRepository;

/**
 * 모든 화면 템플릿에 현재 로그인 사용자 정보를 넣어 준다. 헤더의 계정 영역이 이 값으로 그려진다.
 *
 * <p>여기서 채우는 값은 표시 전용이다. 권한 판단에 쓰이지 않으므로 만료된 access token에서 읽은
 * 사용자 식별자도 허용한다. 그 근거는 아래 {@link #sessionUserId}에 적어 두었다.</p>
 */
@ControllerAdvice
public class WebAccountModel {
	private final UserRepository users;
	private final JwtService jwtService;

	WebAccountModel(UserRepository users, JwtService jwtService) {
		this.users = users;
		this.jwtService = jwtService;
	}

	@ModelAttribute
	void account(@AuthenticationPrincipal SrrrgPrincipal principal, HttpServletRequest request, Model model) {
		populate(principal, request, model);
	}

	/**
	 * @ExceptionHandler 가 렌더하는 뷰에는 @ModelAttribute 어드바이스가 적용되지 않는다.
	 * 그래서 링크 오류 화면은 세션이 살아 있어도 헤더가 로그아웃 상태로 보였다.
	 * 그 경로에서 직접 호출할 수 있게 열어 둔다.
	 */
	public void apply(HttpServletRequest request, Model model) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication == null ? null : authentication.getPrincipal();
		populate(principal instanceof SrrrgPrincipal sessionPrincipal ? sessionPrincipal : null, request, model);
	}

	/**
	 * 사용자를 찾지 못하면 모델에 계정 속성을 넣지 않는다. 템플릿은 속성이 없는 상태를 로그아웃으로 그린다.
	 * 현재 경로는 로그인 후 돌아올 위치로 쓰이므로 로그인 여부와 관계없이 항상 넣는다.
	 */
	private void populate(SrrrgPrincipal principal, HttpServletRequest request, Model model) {
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

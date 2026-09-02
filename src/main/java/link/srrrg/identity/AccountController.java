package link.srrrg.identity;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.SrrrgPrincipal;

/**
 * 로그인한 사용자가 자기 계정 정보를 보고 표시 이름을 바꾸는 웹 API.
 * 주체는 항상 쿠키 JWT에서 오므로 요청 본문의 사용자 식별자를 받지 않는다.
 * 받으면 남의 계정을 지정할 수 있게 된다.
 */
@RestController
@RequestMapping("/api/web/account")
public class AccountController {
	private final UserRepository users;
	private final OAuthAccountRepository accounts;

	public AccountController(UserRepository users, OAuthAccountRepository accounts) {
		this.users = users;
		this.accounts = accounts;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public AccountResponse get(@AuthenticationPrincipal SrrrgPrincipal principal) {
		return response(user(principal.userId()));
	}

	@PatchMapping
	@Transactional
	public AccountResponse update(@AuthenticationPrincipal SrrrgPrincipal principal,
			@Valid @RequestBody UpdateAccountRequest request) {
		User user = user(principal.userId());
		user.updateDisplayName(request.displayName().trim());
		return response(user);
	}

	/**
	 * 온보딩을 마치면서 표시 이름을 확정한다. 이미 마친 사용자가 다시 호출하면 완료 시각이 갱신되며,
	 * 이는 표시 이름 변경과 같은 결과라 별도로 막지 않는다.
	 */
	@PostMapping("/onboarding")
	@Transactional
	public AccountResponse completeOnboarding(@AuthenticationPrincipal SrrrgPrincipal principal,
			@Valid @RequestBody CompleteOnboardingRequest request) {
		User user = user(principal.userId());
		user.completeOnboarding(request.displayName().trim());
		return response(user);
	}

	private User user(Long userId) {
		return users.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	/**
	 * 연결된 공급자 목록을 함께 담는다. 화면에서 어떤 공급자로 로그인할 수 있는지 보여주기 위한 것이며,
	 * 공급자 사용자 식별자나 공급자 쪽 이메일은 내보내지 않는다.
	 */
	private AccountResponse response(User user) {
		List<String> providers = accounts.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
				.map(account -> account.getProvider().name())
				.toList();
		return new AccountResponse(user.getId(), user.getEmail(), user.getDisplayName(), providers, user.getCreatedAt());
	}

	public record UpdateAccountRequest(@NotBlank @Size(max = 100) String displayName) { }
	public record CompleteOnboardingRequest(@NotBlank @Size(max = 100) String displayName) { }
	public record AccountResponse(Long id, String email, String displayName, List<String> providers, Instant createdAt) { }
}

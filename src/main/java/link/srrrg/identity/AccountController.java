package link.srrrg.identity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import link.srrrg.auth.SrrrgPrincipal;

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

	@PostMapping("/onboarding")
	@Transactional
	public AccountResponse completeOnboarding(@AuthenticationPrincipal SrrrgPrincipal principal,
			@Valid @RequestBody CompleteOnboardingRequest request) {
		User user = user(principal.userId());
		user.completeOnboarding(request.displayName().trim(), request.email().trim().toLowerCase(Locale.ROOT));
		return response(user);
	}

	private User user(Long userId) {
		return users.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	private AccountResponse response(User user) {
		List<String> providers = accounts.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
				.map(account -> account.getProvider().name())
				.toList();
		return new AccountResponse(user.getId(), user.getEmail(), user.getDisplayName(), providers, user.getCreatedAt());
	}

	public record UpdateAccountRequest(@NotBlank @Size(max = 100) String displayName) { }
	public record CompleteOnboardingRequest(
			@NotBlank @Size(max = 100) String displayName,
			@NotBlank @Email @Size(max = 320) String email) { }
	public record AccountResponse(Long id, String email, String displayName, List<String> providers, Instant createdAt) { }
}

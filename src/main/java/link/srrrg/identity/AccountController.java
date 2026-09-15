package link.srrrg.identity;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 *
 * <p>사용자와 연결 공급자 조회, 상태 변경과 트랜잭션은 {@link AccountService}가 담당한다.
 * 이 컨트롤러는 인증된 사용자 id와 검증된 요청 값을 전달하고 HTTP 응답으로 변환한다.</p>
 */
@RestController
@RequestMapping("/api/web/account")
public class AccountController {
	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping
	public AccountResponse get(@AuthenticationPrincipal SrrrgPrincipal principal) {
		return AccountResponse.from(accountService.get(principal.userId()));
	}

	@PatchMapping
	public AccountResponse update(@AuthenticationPrincipal SrrrgPrincipal principal,
			@Valid @RequestBody UpdateAccountRequest request) {
		return AccountResponse.from(accountService.updateDisplayName(principal.userId(), request.displayName()));
	}

	/**
	 * 온보딩을 마치면서 표시 이름을 확정한다. 이미 마친 사용자가 다시 호출하면 완료 시각이 갱신되며,
	 * 이는 표시 이름 변경과 같은 결과라 별도로 막지 않는다.
	 */
	@PostMapping("/onboarding")
	public AccountResponse completeOnboarding(@AuthenticationPrincipal SrrrgPrincipal principal,
			@Valid @RequestBody CompleteOnboardingRequest request) {
		return AccountResponse.from(accountService.completeOnboarding(principal.userId(), request.displayName()));
	}

	public record UpdateAccountRequest(@NotBlank @Size(max = 100) String displayName) { }
	public record CompleteOnboardingRequest(@NotBlank @Size(max = 100) String displayName) { }
	public record AccountResponse(Long id, String email, String displayName, List<String> providers, Instant createdAt) {
		static AccountResponse from(AccountProfile profile) {
			return new AccountResponse(profile.id(), profile.email(), profile.displayName(),
					profile.providers(), profile.createdAt());
		}
	}
}

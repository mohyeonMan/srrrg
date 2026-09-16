package link.srrrg.identity.account.service;

import link.srrrg.identity.account.model.AccountProfile;
import link.srrrg.identity.account.model.User;
import link.srrrg.identity.account.repository.UserRepository;
import link.srrrg.identity.connection.repository.OAuthAccountRepository;
import link.srrrg.identity.connection.service.OAuthIdentityService;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 사용자의 계정 프로필 조회와 변경을 담당하는 application 경계다. 사용자 식별자는 인증된
 * principal에서 전달받고, 요청이 다른 사용자 id를 지정할 수 없도록 별도 대상 id는 받지 않는다.
 *
 * <p>OAuth 로그인 신원을 사용자와 연결하는 {@link OAuthIdentityService}와 달리, 이 클래스는 이미
 * 인증된 사용자의 표시 정보와 온보딩 상태만 다룬다. 연결된 공급자는 로그인 가능 수단 표시용 이름만
 * 결과에 포함하며 공급자 사용자 식별자와 공급자 이메일은 노출하지 않는다.</p>
 */
@Service
public class AccountService {
	private final UserRepository userRepository;
	private final OAuthAccountRepository accountRepository;

	public AccountService(UserRepository userRepository, OAuthAccountRepository accountRepository) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
	}

	@Transactional(readOnly = true)
	public AccountProfile get(Long userId) {
		return profile(user(userId));
	}

	/**
	 * 표시 이름 양끝의 공백을 제거해 저장하고, 같은 트랜잭션에서 최신 계정 프로필을 반환한다.
	 * 길이와 빈 값 검사는 HTTP 요청 DTO의 Bean Validation이 선행한다.
	 */
	@Transactional
	public AccountProfile updateDisplayName(Long userId, String displayName) {
		User user = user(userId);
		user.updateDisplayName(displayName.trim());
		return profile(user);
	}

	/**
	 * 첫 로그인 온보딩을 완료하면서 표시 이름을 확정한다. 이미 완료한 사용자가 다시 호출해도
	 * 표시 이름 변경으로 허용하며, 엔티티가 완료 시각을 현재 시각으로 갱신한다.
	 */
	@Transactional
	public AccountProfile completeOnboarding(Long userId, String displayName) {
		User user = user(userId);
		user.completeOnboarding(displayName.trim());
		return profile(user);
	}

	private User user(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	/**
	 * 계정 엔티티와 연결 공급자를 외부 응답과 분리된 결과로 조립한다. 공급자는 연결 생성 순서로
	 * 유지해 기존 화면 표시 순서가 바뀌지 않게 한다.
	 */
	private AccountProfile profile(User user) {
		List<String> providers = accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
				.map(account -> account.getProvider().name())
				.toList();
		return new AccountProfile(
				user.getId(), user.getEmail(), user.getDisplayName(), providers, user.getCreatedAt());
	}
}

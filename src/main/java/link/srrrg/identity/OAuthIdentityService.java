package link.srrrg.identity;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import link.srrrg.project.ProjectService;

/**
 * 공급자에서 넘어온 신원을 이 서비스의 사용자 계정으로 확정한다. 로그인마다 계정을 새로 만들지,
 * 기존 계정에 붙일지, 사용자 확인을 먼저 받을지가 여기서 갈린다.
 *
 * <p>매칭 기준은 (공급자, 공급자 사용자 식별자) 쌍이다. 이메일은 바뀔 수 있고 공급자마다 검증 수준이
 * 달라 기준으로 쓰지 않는다. 이메일은 이미 가입한 사용자를 알아보는 보조 단서로만 쓰며,
 * 그때도 자동으로 합치지 않고 확인 절차로 넘긴다.</p>
 *
 * <p>검증되지 않은 이메일은 저장은 하되 계정 매칭에는 쓰지 않는다. 검증되지 않은 주소로 기존 사용자를
 * 찾으면 남의 이메일을 등록한 공급자 계정으로 그 사용자의 자원에 접근할 수 있다.</p>
 */
@Service
@RequiredArgsConstructor
public class OAuthIdentityService {

	private final UserRepository userRepository;
	private final OAuthAccountRepository accountRepository;
	private final ProjectService projectService;

	/**
	 * 로그인 한 건의 결과를 결정한다.
	 *
	 * @param suppliedIdentity 공급자 응답에서 뽑아낸 신원. 정규화 전이라 그대로 저장하지 않는다
	 * @return 인증 완료이거나 계정 연결 확인이 필요한 상태. 후자에서는 아직 아무 계정도 만들어지지 않았다
	 * @throws IllegalArgumentException 공급자 사용자 식별자가 없거나 형식이 맞지 않는 경우
	 */
	@Transactional
	public LoginResolution resolve(OAuthIdentity suppliedIdentity) {
		OAuthIdentity identity = normalize(suppliedIdentity);
		LoginResolution resolution = accountRepository.findByProviderAndProviderUserId(
				identity.provider(), identity.providerUserId())
				.map(account -> existingAccount(account, identity))
				.orElseGet(() -> newAccount(identity));
		if (!resolution.requiresLink()) {
			// 로그인이 확정된 사용자에게만 개인 프로젝트를 보장한다. 연결 확인이 필요한 단계에서 만들면
			// 확인을 끝내지 않은 시도마다 빈 프로젝트가 쌓인다.
			projectService.ensurePersonalProject(resolution.user().getId());
		}
		return resolution;
	}

	/**
	 * 이미 인증된 사용자에게 공급자 계정을 추가로 연결한다. 같은 공급자 계정이 이미 다른 사용자에게
	 * 붙어 있으면 옮기지 않고 실패시킨다. 옮기도록 두면 한 공급자 계정으로 두 사용자를 오갈 수 있다.
	 *
	 * @throws IllegalStateException 그 공급자 계정이 제3의 사용자에게 이미 연결된 경우
	 */
	@Transactional
	public void attach(User user, OAuthIdentity suppliedIdentity) {
		OAuthIdentity identity = normalize(suppliedIdentity);
		accountRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
				.ifPresentOrElse(account -> {
					if (!account.getUser().getId().equals(user.getId())) {
						throw new IllegalStateException("이미 다른 사용자에게 연결된 OAuth 계정입니다.");
					}
				}, () -> accountRepository.save(OAuthAccount.create(user, identity)));
	}

	/**
	 * 이미 연결된 계정의 로그인. 공급자 쪽 이메일과 검증 여부는 바뀔 수 있으므로 로그인할 때마다 갱신하지만,
	 * 사용자 계정의 이메일은 건드리지 않는다. 공급자에서의 변경이 이 서비스의 신원을 바꾸면 안 되기 때문이다.
	 */
	private LoginResolution existingAccount(OAuthAccount account, OAuthIdentity identity) {
		account.recordLogin(identity.providerEmail(), identity.providerEmailVerified());
		return LoginResolution.authenticated(account.getUser());
	}

	/**
	 * 처음 보는 공급자 계정을 처리한다. 검증된 이메일이 없으면 곧바로 새 사용자를 만들고,
	 * 검증된 이메일이 기존 사용자와 겹치면 자동으로 합치지 않고 연결 확인을 요구한다.
	 */
	private LoginResolution newAccount(OAuthIdentity identity) {
		String verifiedEmail = identity.verifiedEmail();
		if (verifiedEmail == null) {
			return createUser(identity, null);
		}
		return userRepository.findByEmail(verifiedEmail)
				.<LoginResolution>map(user -> LoginResolution.linkRequired(user, identity))
				.orElseGet(() -> createUser(identity, verifiedEmail));
	}

	/**
	 * 새 사용자와 공급자 계정을 함께 만든다. {@code verifiedEmail}이 {@code null}이면 이메일 없는 계정이 된다.
	 * 검증된 주소만 users.email에 저장한다는 규칙을 지키기 위해, 미검증 이메일은 여기까지 전달되지 않는다.
	 */
	private LoginResolution createUser(OAuthIdentity identity, String verifiedEmail) {
		User user = userRepository.save(User.create(verifiedEmail, identity.displayName()));
		accountRepository.save(OAuthAccount.create(user, identity));
		return LoginResolution.authenticated(user);
	}

	/**
	 * 공급자가 준 값을 저장 가능한 형태로 다듬는다. 여기서 걸러지지 않은 값은 그대로 DB에 들어간다.
	 *
	 * <p>이메일 정규화에 실패하면 검증 여부도 함께 거짓으로 내린다. 형식이 깨진 주소를 검증된 것으로
	 * 남겨 두면 계정 매칭에 쓰이기 때문이다. 표시 이름은 컬럼 길이에 맞춰 자르고, 비어 있으면
	 * 이메일 앞부분이나 기본 문구로 채운다.</p>
	 */
	private OAuthIdentity normalize(OAuthIdentity identity) {
		if (identity.provider() == null || identity.providerUserId() == null
				|| identity.providerUserId().isBlank() || identity.providerUserId().length() > 255) {
			throw new IllegalArgumentException("OAuth 사용자 식별자가 올바르지 않습니다.");
		}
		String email = normalizeEmail(identity.providerEmail());
		boolean emailVerified = identity.providerEmailVerified() && email != null;
		String name = identity.displayName() == null || identity.displayName().isBlank()
				? defaultDisplayName(email)
				: identity.displayName().trim();
		if (name.length() > 100) {
			name = name.substring(0, 100);
		}
		return new OAuthIdentity(identity.provider(), identity.providerUserId(), email, emailVerified, name);
	}

	/**
	 * 소문자로 맞추고 최소한의 형태만 확인한다. 대소문자를 통일하지 않으면 같은 주소가 다른 사용자로 갈린다.
	 * 완전한 형식 검증을 하지 않는 것은 이 값이 공급자가 이미 검증한 주소라는 전제 때문이며,
	 * 여기서는 컬럼 길이를 넘거나 골뱅이 위치가 잘못된 값만 걸러낸다.
	 *
	 * @return 정규화된 주소, 또는 쓸 수 없는 값이면 {@code null}
	 */
	private String normalizeEmail(String suppliedEmail) {
		if (suppliedEmail == null || suppliedEmail.isBlank()) {
			return null;
		}
		String email = suppliedEmail.trim().toLowerCase(Locale.ROOT);
		int at = email.indexOf('@');
		return email.length() <= 320 && at > 0 && at < email.length() - 1 ? email : null;
	}

	private String defaultDisplayName(String email) {
		return email == null ? "srrrg 사용자" : email.substring(0, email.indexOf('@'));
	}

	/**
	 * 로그인 판정 결과. {@code pendingIdentity}가 있으면 아직 로그인이 끝나지 않았고
	 * 계정 연결 확인이 필요하다는 뜻이므로, 이 상태에서 세션을 발급하면 안 된다.
	 */
	public record LoginResolution(User user, OAuthIdentity pendingIdentity) {
		public static LoginResolution authenticated(User user) {
			return new LoginResolution(user, null);
		}

		public static LoginResolution linkRequired(User user, OAuthIdentity identity) {
			return new LoginResolution(user, identity);
		}

		public boolean requiresLink() {
			return pendingIdentity != null;
		}
	}
}

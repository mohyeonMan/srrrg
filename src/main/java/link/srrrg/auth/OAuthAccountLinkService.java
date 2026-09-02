package link.srrrg.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.OAuthAccount;
import link.srrrg.identity.OAuthAccountRepository;
import link.srrrg.identity.OAuthIdentity;
import link.srrrg.identity.User;
import lombok.RequiredArgsConstructor;

/**
 * 이미 가입된 이메일로 다른 공급자에 로그인했을 때, 두 계정을 합쳐도 되는지 확인하는 절차를 관리한다.
 *
 * <p>공급자가 검증했다는 이메일만으로 자동 연결하지 않는 이유는, 그 이메일의 실제 소유자가 아니어도
 * 공급자 계정을 만들 수 있는 경우가 있기 때문이다. 그래서 대기 요청을 만들어 두고,
 * 사용자가 기존 방식으로 다시 로그인해 본인임을 증명한 뒤에만 연결을 확정한다.</p>
 *
 * <p>대기 토큰도 원문을 저장하지 않는다. 브라우저 쿠키에만 원문이 있고 DB에는 해시만 남는다.</p>
 */
@Service
@RequiredArgsConstructor
public class OAuthAccountLinkService {

	private static final String URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	private static final Duration LIFETIME = Duration.ofMinutes(10);

	private final OAuthAccountLinkRequestRepository requestRepository;
	private final OAuthAccountRepository accountRepository;
	private final SecureRandomStringGenerator random;

	/**
	 * 연결 대기 요청을 만든다. 같은 공급자 계정의 이전 대기 요청을 먼저 지워 하나만 유효하게 유지한다.
	 * 여러 개가 남아 있으면 오래된 쿠키로도 연결이 성사돼, 사용자가 취소한 시도가 되살아난다.
	 *
	 * @return 브라우저 쿠키로 내보낼 원문 토큰. DB에는 이 값의 해시만 남는다
	 */
	@Transactional
	public PendingLink create(User existingUser, OAuthIdentity identity, String returnPath) {
		requestRepository.deleteExpired(Instant.now());
		requestRepository.deleteByProviderAndProviderUserId(identity.provider(), identity.providerUserId());
		// 삭제를 먼저 반영해야 뒤이은 저장이 같은 공급자 계정의 유일성 제약에 걸리지 않는다.
		requestRepository.flush();
		String raw = random.generate(URL_SAFE, 43);
		requestRepository.save(OAuthAccountLinkRequest.create(
				TokenHash.sha256(raw), existingUser, identity, returnPath, Instant.now().plus(LIFETIME)));
		return new PendingLink(raw);
	}

	/**
	 * 대기 중인 연결을 확정한다. 이 메서드의 검사 세 가지가 계정 탈취를 막는 실질적인 장치다.
	 *
	 * <p>만료를 확인하고, 대기 요청이 지목한 사용자와 방금 로그인한 사용자가 같은지 보고,
	 * 그 사용자의 현재 이메일이 대기 요청에 기록된 이메일과 여전히 같은지 확인한다.
	 * 마지막 검사가 없으면 대기 요청을 만든 뒤 이메일이 바뀐 계정에도 연결이 성사된다.</p>
	 *
	 * @param rawToken 쿠키에 있던 대기 토큰 원문
	 * @param authenticatedUser 기존 방식으로 다시 로그인해 확인된 사용자
	 * @return 연결 후 돌아갈 경로. 대기 요청은 성공·만료 어느 쪽이든 삭제된다
	 * @throws IllegalArgumentException 토큰이 없거나 만료됐거나 다른 사용자로 로그인한 경우
	 * @throws IllegalStateException 그 공급자 계정이 이미 제3의 사용자에게 연결돼 있는 경우
	 */
	@Transactional
	public String complete(String rawToken, User authenticatedUser) {
		OAuthAccountLinkRequest request = requestRepository.findByHashForUpdate(TokenHash.sha256(rawToken))
				.orElseThrow(() -> new IllegalArgumentException("계정 연결 요청이 유효하지 않습니다."));
		if (!request.getExpiresAt().isAfter(Instant.now())) {
			requestRepository.delete(request);
			throw new IllegalArgumentException("계정 연결 요청이 만료되었습니다.");
		}
		if (!request.getExistingUserId().equals(authenticatedUser.getId())
				|| !request.getProviderEmail().equals(authenticatedUser.getEmail())) {
			throw new IllegalArgumentException("기존 계정으로 다시 로그인해야 합니다.");
		}
		accountRepository.findByProviderAndProviderUserId(request.getProvider(), request.getProviderUserId())
				.ifPresentOrElse(account -> {
					if (!account.getUser().getId().equals(authenticatedUser.getId())) {
						throw new IllegalStateException("이미 다른 사용자에게 연결된 OAuth 계정입니다.");
					}
				}, () -> accountRepository.save(OAuthAccount.create(authenticatedUser, request.identity())));
		requestRepository.delete(request);
		return request.getReturnPath();
	}

	public record PendingLink(String rawToken) {
	}
}

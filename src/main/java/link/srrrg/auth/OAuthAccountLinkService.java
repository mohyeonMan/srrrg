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

@Service
@RequiredArgsConstructor
public class OAuthAccountLinkService {

	private static final String URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	private static final Duration LIFETIME = Duration.ofMinutes(10);

	private final OAuthAccountLinkRequestRepository requestRepository;
	private final OAuthAccountRepository accountRepository;
	private final SecureRandomStringGenerator random;

	@Transactional
	public PendingLink create(User existingUser, OAuthIdentity identity, String returnPath) {
		requestRepository.deleteExpired(Instant.now());
		requestRepository.deleteByProviderAndProviderUserId(identity.provider(), identity.providerUserId());
		requestRepository.flush();
		String raw = random.generate(URL_SAFE, 43);
		requestRepository.save(OAuthAccountLinkRequest.create(
				TokenHash.sha256(raw), existingUser, identity, returnPath, Instant.now().plus(LIFETIME)));
		return new PendingLink(raw);
	}

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

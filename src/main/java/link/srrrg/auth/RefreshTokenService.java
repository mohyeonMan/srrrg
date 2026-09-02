package link.srrrg.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;
import lombok.RequiredArgsConstructor;

/**
 * 브라우저 세션의 수명을 관리한다. access token과 달리 이쪽은 DB에 상태가 있어 취소가 가능하며,
 * 로그아웃과 탈취 대응이 실제로 이루어지는 지점이다.
 *
 * <p>회전(rotation) 방식이다. refresh token은 한 번 쓰면 사용 표시가 되고 새 토큰으로 교체된다.
 * 같은 토큰이 두 번 들어오면 원문을 가진 쪽이 둘이라는 뜻이므로, 어느 쪽이 정상 사용자인지 판단하지 않고
 * 같은 계열(family)의 토큰을 모두 폐기해 양쪽 다 로그아웃시킨다.</p>
 *
 * <p>원문은 어디에도 저장하지 않는다. 발급 순간에만 호출자에게 넘기고 DB에는 해시만 남으므로,
 * DB가 통째로 유출돼도 그것만으로 세션을 이어받을 수는 없다.</p>
 *
 * <p>여러 파드가 같은 토큰의 회전을 동시에 처리할 수 있으므로 조회에 행 잠금을 건다
 * ({@link RefreshTokenRepository#findByHashForUpdate}). JVM 안의 lock으로는 파드를 가로지르지 못한다.</p>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private static final String URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	// 30일. 이 기간이 곧 로그인 상태 유지의 상한이며, 회전할 때마다 새 토큰에 다시 30일이 부여되므로
	// 계속 사용하는 사용자는 사실상 만료되지 않는다. 방치된 세션만 이 기간 뒤에 끊긴다.
	private static final Duration LIFETIME = Duration.ofDays(30);

	private final RefreshTokenRepository repository;
	private final SecureRandomStringGenerator random;

	/**
	 * 로그인 직후 새 세션을 시작한다. 새 family를 부여하므로 다른 브라우저에 열려 있던 세션은 그대로 유지된다.
	 *
	 * @return 저장된 행의 id와 원문 토큰. 원문은 이 반환값에만 존재하며 DB에는 해시만 남는다
	 */
	@Transactional
	public IssuedRefreshToken issue(User user) {
		return create(user, UUID.randomUUID());
	}

	/**
	 * refresh token 하나를 소비하고 같은 family의 새 토큰으로 교체한다.
	 *
	 * <p>{@code noRollbackFor}가 중요하다. 재사용을 발견하면 family 전체를 폐기한 뒤 예외를 던지는데,
	 * 기본 설정대로면 그 예외가 트랜잭션을 롤백시켜 방금 한 폐기까지 되돌린다.
	 * 그러면 탈취를 감지하고도 아무 조치가 남지 않는다.</p>
	 *
	 * @param rawToken 쿠키에서 꺼낸 refresh token 원문
	 * @return 토큰의 주인과 새로 발급된 원문 토큰
	 * @throws RefreshTokenReuseException 이미 사용된 토큰이 다시 들어온 경우. 이때 같은 family가 모두 폐기된다
	 * @throws InvalidRefreshTokenException 형식이 틀렸거나, 없는 토큰이거나, 이미 폐기·만료된 경우
	 */
	@Transactional(noRollbackFor = RefreshTokenReuseException.class)
	public RotatedRefreshToken rotate(String rawToken) {
		validateFormat(rawToken);
		// 행 잠금으로 조회한다. 같은 토큰으로 두 요청이 동시에 들어오면 하나가 끝날 때까지 다른 하나가 대기하므로,
		// 둘 다 사용되지 않은 상태로 읽고 각자 새 토큰을 발급하는 상황이 생기지 않는다.
		RefreshToken current = repository.findByHashForUpdate(TokenHash.sha256(rawToken))
				.orElseThrow(InvalidRefreshTokenException::new);
		Instant now = Instant.now();
		// 이미 쓴 토큰이 다시 왔다는 것은 원문 사본이 둘 이상 존재한다는 뜻이다. 정상 사용자와 탈취자를
		// 구분할 방법이 없으므로 계열 전체를 끊고 양쪽 모두 다시 로그인하게 한다.
		if (current.getUsedAt() != null) {
			repository.revokeFamily(current.getTokenFamilyId(), now);
			throw new RefreshTokenReuseException();
		}
		if (current.getRevokedAt() != null || current.expiredAt(now)) {
			throw new InvalidRefreshTokenException();
		}
		IssuedRefreshToken replacement = create(current.getUser(), current.getTokenFamilyId());
		current.replaceWith(replacement.id(), now);
		return new RotatedRefreshToken(current.getUser(), replacement.rawToken());
	}

	/**
	 * 이 브라우저의 세션만 종료한다. family 단위로 폐기하므로 회전 과정에서 만들어진 토큰들도 함께 끊긴다.
	 * 다른 기기의 로그인은 각각 다른 family라 영향을 받지 않는다.
	 */
	@Transactional
	public void logoutCurrent(String rawToken) {
		RefreshToken token = find(rawToken);
		repository.revokeFamily(token.getTokenFamilyId(), Instant.now());
	}

	/**
	 * 이 사용자의 모든 기기 세션을 종료한다. 토큰 탈취가 의심될 때 쓰는 수단이므로
	 * 이미 사용·폐기·만료된 토큰으로는 실행할 수 없게 상태를 먼저 확인한다.
	 *
	 * @throws InvalidRefreshTokenException 현재 유효한 세션이 아닌 토큰으로 요청한 경우
	 */
	@Transactional
	public void logoutAll(String rawToken) {
		RefreshToken token = find(rawToken);
		if (token.getUsedAt() != null || token.getRevokedAt() != null || token.expiredAt(Instant.now())) {
			throw new InvalidRefreshTokenException();
		}
		repository.revokeAllForUser(token.getUser().getId(), Instant.now());
	}

	private RefreshToken find(String rawToken) {
		validateFormat(rawToken);
		return repository.findByHashForUpdate(TokenHash.sha256(rawToken))
				.orElseThrow(InvalidRefreshTokenException::new);
	}

	/**
	 * 토큰 원문을 만들고 해시만 저장한다. 접두사 srrrg_rt_는 유출된 문자열이 어떤 용도인지 바로 알아보게 하려는 표시다.
	 * 뒤의 43자는 64자 집합에서 뽑으므로 약 256비트에 해당한다.
	 *
	 * <p>{@code saveAndFlush}로 즉시 반영하는 이유는 호출자가 새 행의 id를 곧바로 필요로 하기 때문이다.
	 * 회전에서 이전 토큰의 {@code replacedByTokenId}에 이 id를 기록한다.</p>
	 */
	private IssuedRefreshToken create(User user, UUID familyId) {
		String raw = "srrrg_rt_" + random.generate(URL_SAFE, 43);
		RefreshToken saved = repository.saveAndFlush(RefreshToken.create(
				user, TokenHash.sha256(raw), familyId, Instant.now().plus(LIFETIME)));
		return new IssuedRefreshToken(saved.getId(), raw);
	}

	/**
	 * 형태가 맞지 않는 값은 해시와 DB 조회에 들어가기 전에 걸러낸다. 쿠키 값은 클라이언트가 임의로 채울 수 있어
	 * 아무 문자열이나 조회 트래픽으로 이어지는 것을 막는다. 실패는 조회 실패와 같은 예외로 합친다.
	 */
	private void validateFormat(String rawToken) {
		if (rawToken == null || !rawToken.matches("srrrg_rt_[A-Za-z0-9_-]{43}")) {
			throw new InvalidRefreshTokenException();
		}
	}

	public record IssuedRefreshToken(Long id, String rawToken) {
	}

	public record RotatedRefreshToken(User user, String rawToken) {
	}
}

package link.srrrg.auth;

import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 웹 세션의 access token을 발급하고 검증한다. 서버에 세션 상태를 두지 않는 STATELESS 구조라
 * 이 토큰의 서명 검증 결과만으로 요청 주체가 정해진다.
 *
 * <p>서명 키는 설정에서 읽는 대칭키(HS256)다. 모든 파드가 같은 키를 공유하므로 어느 파드가 발급한 토큰이든
 * 다른 파드가 그대로 검증할 수 있고, 세션 저장소나 sticky session이 필요 없다. 그 대가로 키가 유출되면
 * 누구든 토큰을 위조할 수 있으므로 키는 설정으로만 주입하고 코드나 로그에 남기지 않는다.</p>
 *
 * <p>키 교체를 위해 활성 키와 직전 키를 함께 들고 있다. 발급은 항상 활성 키로 하고 검증은 두 키 모두 허용하므로,
 * 새 키로 바꾼 뒤에도 아직 살아 있는 기존 토큰이 즉시 무효가 되지 않는다. 직전 키 설정을 지우는 시점이
 * 곧 그 키로 발급된 토큰이 모두 끊기는 시점이다.</p>
 *
 * <p>발급한 토큰은 만료 전에는 취소할 수 없다. 그래서 유효기간을 분 단위로 짧게 두고, 실제 세션 유지와
 * 강제 로그아웃은 DB에 상태가 있는 {@link RefreshTokenService}가 담당한다.</p>
 */
@Service
public class JwtService {

	// 이 서비스가 발급한 토큰만 받아들이기 위한 고정 수신자. 검증에서 이 값을 확인하므로,
	// 같은 서명 키를 쓰는 다른 용도의 토큰이 생겨도 웹 세션 인증에는 통과하지 못한다.
	private static final String AUDIENCE = "srrrg-web";

	private final JwtProperties properties;
	private final String issuer;
	private final Duration accessLifetime;

	/**
	 * 발급에 필요한 설정을 받고 유효시간을 검증한다. 0이나 음수 유효시간은 발급 즉시 만료된 토큰을 만들어
	 * 로그인 직후 로그아웃되는 상태가 되므로 기동 시점에 막는다.
	 */
	public JwtService(JwtProperties properties, @Value("${srrrg.base-url}") String issuer,
			@Value("${srrrg.auth.jwt.access-lifetime:5m}") Duration accessLifetime) {
		this.properties = properties;
		this.issuer = issuer;
		if (accessLifetime.isZero() || accessLifetime.isNegative()) {
			throw new IllegalArgumentException("access JWT 유효시간은 0보다 커야 합니다.");
		}
		this.accessLifetime = accessLifetime;
	}

	/**
	 * 사용자 한 명에 대한 access token을 발급한다.
	 *
	 * @param userId 토큰의 subject로 들어갈 사용자 식별자
	 * @return 직렬화된 JWS 문자열. 호출자는 이 값을 HttpOnly 쿠키로만 내보낸다
	 * @throws IllegalStateException 서명 키가 설정되지 않았거나 서명에 실패한 경우
	 */
	public String issue(Long userId) {
		Key active = activeKey();
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.audience(AUDIENCE)
				.subject(userId.toString())
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(accessLifetime)))
				.jwtID(UUID.randomUUID().toString())
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
			// 검증 쪽이 어떤 키로 서명됐는지 알아야 키 교체 중에도 두 키를 구분할 수 있으므로 kid를 헤더에 넣는다.
				.keyID(active.kid()).build(), claims);
		try {
			jwt.sign(new MACSigner(active.secretKey().getEncoded()));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException("access JWT를 발급할 수 없습니다.", exception);
		}
	}

	/**
	 * 인증에 쓰는 검증 경로. 서명, 발급자, 수신자, 만료를 모두 확인한 뒤 사용자 식별자를 돌려준다.
	 *
	 * @param rawJwt 쿠키에서 꺼낸 토큰 문자열
	 * @return 토큰이 가리키는 사용자 식별자
	 * @throws IllegalArgumentException 형식·서명·claim 중 하나라도 어긋난 경우. 어떤 항목이 틀렸는지는 구분해 알리지 않는다
	 */
	public Long verify(String rawJwt) {
		return verify(rawJwt, true);
	}

	/**
	 * 만료만 무시하고 서명과 나머지 claim 은 그대로 검증한다.
	 * access token 이 만료돼도 refresh token 으로 세션이 이어지는데, 페이지를 새로 열면
	 * 서버가 이를 알 수 없어 헤더가 로그아웃으로 그려지던 문제를 해결하기 위한 표시 전용 경로다.
	 * 인증에는 쓰지 않는다. {@link JwtAuthenticationFilter} 는 계속 {@link #verify(String)} 만 쓴다.
	 */
	public Long readSubjectAllowingExpired(String rawJwt) {
		return verify(rawJwt, false);
	}

	/**
	 * 검증 본체. {@code rejectExpired}가 거짓이면 만료만 통과시키고 나머지 검사는 그대로 수행한다.
	 * 실패 원인을 하나의 예외로 합쳐 던지므로 호출자는 어떤 검사가 실패했는지 알 수 없다.
	 */
	private Long verify(String rawJwt, boolean rejectExpired) {
		try {
			SignedJWT jwt = SignedJWT.parse(rawJwt);
			// 알고리즘을 헤더 값에 맡기지 않고 HS256으로 고정한다. 헤더를 그대로 믿으면 공격자가
			// alg를 none이나 다른 방식으로 바꿔 보낸 토큰까지 검증 대상이 된다.
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
				throw invalid();
			}
			// kid가 우리가 가진 키 목록에 없으면 검증 자체를 하지 않는다. 키 교체 기간에는 두 개가 들어 있다.
			Key key = verificationKeys().get(jwt.getHeader().getKeyID());
			if (key == null || !jwt.verify(new MACVerifier(key.secretKey().getEncoded()))) {
				throw invalid();
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Instant now = Instant.now();
			if (!issuer.equals(claims.getIssuer())
					|| claims.getAudience() == null
					|| !claims.getAudience().contains(AUDIENCE)
					|| claims.getExpirationTime() == null
					|| (rejectExpired && !claims.getExpirationTime().toInstant().isAfter(now))
					|| claims.getIssueTime() == null
				// 발급 시각이 미래인 토큰은 거부하되 60초는 허용한다. 파드 사이 시계 차이로
				// 방금 발급한 토큰이 다른 파드에서 거부되는 것을 막기 위한 여유다.
					|| claims.getIssueTime().toInstant().isAfter(now.plusSeconds(60))
					|| claims.getJWTID() == null) {
				throw invalid();
			}
			return Long.valueOf(claims.getSubject());
		} catch (ParseException | JOSEException | NumberFormatException exception) {
			throw invalid();
		}
	}

	/**
	 * 토큰을 발급하기 전에 발급 환경이 안전한지 확인한다.
	 *
	 * <p>서명 키가 없으면 여기서 먼저 실패시킨다. 더 중요한 것은 HTTPS 확인이다. 세션 토큰은 쿠키로 나가는데
	 * 평문 HTTP에서는 쿠키의 secure 속성도 켜지지 않아 네트워크에서 그대로 탈취될 수 있다.
	 * 로컬 개발 주소만 예외로 두고, 그 밖의 http 주소에서는 아예 발급하지 않는다.</p>
	 *
	 * @throws IllegalStateException 서명 키가 없거나 HTTPS가 아닌 외부 주소에서 발급을 시도한 경우
	 */
	public void requireConfigured() {
		activeKey();
		URI uri = URI.create(issuer);
		String host = uri.getHost();
		boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
				&& ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
						|| "::1".equals(host) || "[::1]".equals(host));
		if (!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp) {
			throw new IllegalStateException("HTTPS base URL에서만 token을 발급할 수 있습니다.");
		}
	}

	/**
	 * 검증에 허용할 키 목록을 만든다. 직전 키 설정이 비어 있으면 활성 키 하나만 돌려주므로,
	 * 직전 키를 지우는 것이 곧 그 키로 발급된 토큰을 전부 무효화하는 방법이다.
	 */
	private Map<String, Key> verificationKeys() {
		Key active = activeKey();
		if (blank(properties.previousKid()) || blank(properties.previousKeyBase64())) {
			return Map.of(active.kid(), active);
		}
		Key previous = key(properties.previousKid(), properties.previousKeyBase64());
		return Map.of(active.kid(), active, previous.kid(), previous);
	}

	private Key activeKey() {
		if (blank(properties.activeKid()) || blank(properties.activeKeyBase64())) {
			throw new IllegalStateException("JWT signing key가 설정되지 않았습니다.");
		}
		return key(properties.activeKid(), properties.activeKeyBase64());
	}

	/**
	 * Base64로 설정된 키를 HMAC 키로 바꾸면서 최소 길이를 확인한다.
	 * HS256에서 키가 해시 출력보다 짧으면 서명 강도가 그만큼 떨어지므로 256비트 미만은 거부한다.
	 */
	private Key key(String kid, String encoded) {
		try {
			byte[] bytes = Base64.getDecoder().decode(encoded);
			if (bytes.length < 32) {
				throw new IllegalStateException("JWT signing key는 256bit 이상이어야 합니다.");
			}
			SecretKey secretKey = new SecretKeySpec(bytes, "HmacSHA256");
			return new Key(kid, secretKey);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("JWT signing key는 Base64 형식이어야 합니다.", exception);
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * 모든 검증 실패를 같은 예외와 같은 문구로 합친다. 서명이 틀렸는지, 만료됐는지, 수신자가 다른지를
	 * 응답으로 구분해 주면 토큰을 조작하며 시도하는 쪽에 힌트가 된다.
	 */
	private IllegalArgumentException invalid() {
		return new IllegalArgumentException("access JWT가 유효하지 않습니다.");
	}

	private record Key(String kid, SecretKey secretKey) {
	}
}

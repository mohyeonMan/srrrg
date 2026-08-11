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

@Service
public class JwtService {

	private static final String AUDIENCE = "srrrg-web";

	private final JwtProperties properties;
	private final String issuer;
	private final Duration accessLifetime;

	public JwtService(JwtProperties properties, @Value("${srrrg.base-url}") String issuer,
			@Value("${srrrg.auth.jwt.access-lifetime:5m}") Duration accessLifetime) {
		this.properties = properties;
		this.issuer = issuer;
		if (accessLifetime.isZero() || accessLifetime.isNegative()) {
			throw new IllegalArgumentException("access JWT 유효시간은 0보다 커야 합니다.");
		}
		this.accessLifetime = accessLifetime;
	}

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
				.keyID(active.kid()).build(), claims);
		try {
			jwt.sign(new MACSigner(active.secretKey().getEncoded()));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException("access JWT를 발급할 수 없습니다.", exception);
		}
	}

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

	private Long verify(String rawJwt, boolean rejectExpired) {
		try {
			SignedJWT jwt = SignedJWT.parse(rawJwt);
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
				throw invalid();
			}
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
					|| claims.getIssueTime().toInstant().isAfter(now.plusSeconds(60))
					|| claims.getJWTID() == null) {
				throw invalid();
			}
			return Long.valueOf(claims.getSubject());
		} catch (ParseException | JOSEException | NumberFormatException exception) {
			throw invalid();
		}
	}

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

	private IllegalArgumentException invalid() {
		return new IllegalArgumentException("access JWT가 유효하지 않습니다.");
	}

	private record Key(String kid, SecretKey secretKey) {
	}
}

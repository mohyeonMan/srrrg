package link.srrrg.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "oauth_authorization_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 진행 중인 OAuth 인가 요청을 파드 사이에서 공유하기 위한 저장 형태.
 * 세션을 쓰지 않으므로 콜백이 다른 파드에 도착해도 이 행으로 원래 요청을 복원한다.
 *
 * <p>{@code tokenHash}는 브라우저 쿠키 값의 해시이며 조회 키다. {@code codeVerifier}만 원문으로 남는데,
 * 토큰 교환 때 공급자에게 그대로 보내야 하는 값이기 때문이다. 그래서 수명을 10분으로 짧게 두고
 * 사용 즉시 행을 지운다.</p>
 */
public class OAuthAuthorizationRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false, unique = true, length = 255)
	private String state;

	@Column(name = "registration_id", nullable = false, length = 20)
	private String registrationId;

	@Column(name = "authorization_uri", nullable = false, length = 1000)
	private String authorizationUri;

	@Column(name = "client_id", nullable = false, length = 255)
	private String clientId;

	@Column(name = "redirect_uri", nullable = false, length = 1000)
	private String redirectUri;

	@Column(nullable = false, length = 1000)
	private String scopes;

	@Column(name = "code_verifier", nullable = false, length = 255)
	private String codeVerifier;

	@Column(name = "code_challenge", nullable = false, length = 255)
	private String codeChallenge;

	@Column(name = "return_path", nullable = false, length = 1000)
	private String returnPath;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public static OAuthAuthorizationRequest create(String tokenHash, String state, String registrationId,
			String authorizationUri, String clientId, String redirectUri, String scopes,
			String codeVerifier, String codeChallenge, String returnPath, Instant expiresAt) {
		OAuthAuthorizationRequest request = new OAuthAuthorizationRequest();
		request.tokenHash = tokenHash;
		request.state = state;
		request.registrationId = registrationId;
		request.authorizationUri = authorizationUri;
		request.clientId = clientId;
		request.redirectUri = redirectUri;
		request.scopes = scopes;
		request.codeVerifier = codeVerifier;
		request.codeChallenge = codeChallenge;
		request.returnPath = returnPath;
		request.expiresAt = expiresAt;
		return request;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}

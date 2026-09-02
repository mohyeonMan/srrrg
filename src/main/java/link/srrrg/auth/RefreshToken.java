package link.srrrg.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import link.srrrg.identity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발급된 refresh token 한 건의 상태. 원문 대신 해시를 저장하며, 이 행의 상태 변화가 곧 세션의 수명이다.
 *
 * <p>{@code tokenFamilyId}는 하나의 로그인에서 회전을 거쳐 이어지는 토큰들을 묶는다. 재사용이 감지되면
 * 개별 토큰이 아니라 이 family 전체를 폐기해 회전 사슬 전체를 끊는다.</p>
 *
 * <p>사용됨({@code usedAt})과 폐기됨({@code revokedAt})은 다른 상태다. 전자는 정상 회전으로 소비된 것이고
 * 후자는 로그아웃이나 탈취 대응으로 강제 종료된 것이라, 재사용 감지에서 두 경우를 구분해야 한다.</p>
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// unique 제약이 중복 저장을 DB 차원에서 막는다. 길이 64는 SHA-256 16진 표현의 고정 길이다.
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "token_family_id", nullable = false)
	private UUID tokenFamilyId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by_token_id")
	private Long replacedByTokenId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.tokenFamilyId = familyId;
		this.expiresAt = expiresAt;
	}

	public static RefreshToken create(User user, String tokenHash, UUID familyId, Instant expiresAt) {
		return new RefreshToken(user, tokenHash, familyId, expiresAt);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	/**
	 * 정상 회전으로 소비 처리한다. 이 시점부터 같은 토큰이 다시 들어오면 재사용으로 판정된다.
	 * 폐기와 달리 세션을 끊는 것이 아니라 후속 토큰으로 넘겨주는 것이므로 {@code revokedAt}은 건드리지 않는다.
	 */
	public void replaceWith(Long replacementId, Instant now) {
		usedAt = now;
		replacedByTokenId = replacementId;
	}

	/**
	 * 만료 시각과 같은 순간도 만료로 본다. 한 요청 안에서 같은 시각 기준으로 판정하도록 호출자가 현재 시각을 넘긴다.
	 */
	public boolean expiredAt(Instant now) {
		return !expiresAt.isAfter(now);
	}
}

package link.srrrg.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * refresh token 행의 조회와 일괄 폐기를 담당한다. 세션 종료가 어디까지 반영되는지가 여기 쿼리의 범위로 정해진다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	/**
	 * 회전과 로그아웃에서 쓰는 조회. 비관적 쓰기 잠금을 걸어 같은 토큰을 처리하는 다른 파드의 트랜잭션을 대기시킨다.
	 * 이 잠금이 없으면 동시에 들어온 두 요청이 모두 미사용 상태를 읽어 재사용 판정을 빠져나가고 세션이 갈라진다.
	 *
	 * <p>{@code user}를 함께 로딩하는 것은 호출자가 소유자를 바로 쓰기 때문이다.</p>
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "user")
	@Query("select token from RefreshToken token where token.tokenHash = :hash")
	Optional<RefreshToken> findByHashForUpdate(@Param("hash") String hash);

	@Modifying
	@Query("update RefreshToken token set token.revokedAt = :now "
			+ "where token.tokenFamilyId = :familyId and token.revokedAt is null")
	/**
	 * 한 로그인 계열의 살아 있는 토큰을 모두 폐기한다. 이미 폐기된 행은 조건에서 제외해 최초 폐기 시각을 보존한다.
	 * 벌크 update라 영속성 컨텍스트의 엔티티에는 반영되지 않으므로 같은 트랜잭션에서 이후에 읽는 값을 믿지 않는다.
	 */
	int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

	@Modifying
	@Query("update RefreshToken token set token.revokedAt = :now "
			+ "where token.user.id = :userId and token.revokedAt is null")
	/**
	 * 사용자의 모든 계열을 폐기한다. 모든 기기 로그아웃의 실제 동작이며,
	 * 커밋되는 즉시 다른 파드에서 진행 중인 회전도 폐기된 토큰을 보게 된다.
	 */
	int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}

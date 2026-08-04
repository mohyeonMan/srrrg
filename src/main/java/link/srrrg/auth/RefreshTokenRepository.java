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

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "user")
	@Query("select token from RefreshToken token where token.tokenHash = :hash")
	Optional<RefreshToken> findByHashForUpdate(@Param("hash") String hash);

	@Modifying
	@Query("update RefreshToken token set token.revokedAt = :now "
			+ "where token.tokenFamilyId = :familyId and token.revokedAt is null")
	int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

	@Modifying
	@Query("update RefreshToken token set token.revokedAt = :now "
			+ "where token.user.id = :userId and token.revokedAt is null")
	int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}

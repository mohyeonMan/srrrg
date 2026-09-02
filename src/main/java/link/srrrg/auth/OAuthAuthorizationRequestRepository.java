package link.srrrg.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 진행 중인 OAuth 인가 요청 행을 다룬다. 조회는 항상 쿠키 값의 해시로만 하며 state로 찾지 않는다.
 */
public interface OAuthAuthorizationRequestRepository extends JpaRepository<OAuthAuthorizationRequest, Long> {
	Optional<OAuthAuthorizationRequest> findByTokenHash(String tokenHash);

	@Modifying
	@Query("delete from OAuthAuthorizationRequest request where request.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}

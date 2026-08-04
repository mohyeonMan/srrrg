package link.srrrg.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthAuthorizationRequestRepository extends JpaRepository<OAuthAuthorizationRequest, Long> {
	Optional<OAuthAuthorizationRequest> findByTokenHash(String tokenHash);

	@Modifying
	@Query("delete from OAuthAuthorizationRequest request where request.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}

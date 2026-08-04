package link.srrrg.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import link.srrrg.identity.OAuthProvider;

public interface OAuthAccountLinkRequestRepository extends JpaRepository<OAuthAccountLinkRequest, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from OAuthAccountLinkRequest request where request.tokenHash = :hash")
	Optional<OAuthAccountLinkRequest> findByHashForUpdate(@Param("hash") String hash);

	void deleteByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

	@Modifying
	@Query("delete from OAuthAccountLinkRequest request where request.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}

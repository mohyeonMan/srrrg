package link.srrrg.identity;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

	List<OAuthAccount> findByUserIdOrderByCreatedAtAsc(Long userId);
}

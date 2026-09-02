package link.srrrg.identity;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공급자 계정 연결을 조회한다. 로그인 경로에서 소유 사용자를 바로 쓰므로 함께 로딩한다.
 */
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

	List<OAuthAccount> findByUserIdOrderByCreatedAtAsc(Long userId);
}

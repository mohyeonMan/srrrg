package link.srrrg.link;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

	Optional<Link> findByCode(String code);

	@Modifying
	@Query("update Link l set l.clickCount = l.clickCount + 1 where l.code = :code")
	int incrementClickCountByCode(@Param("code") String code);

	@Modifying
	@Query("update Link l set l.redirectCount = l.redirectCount + 1 where l.code = :code")
	int incrementRedirectCountByCode(@Param("code") String code);

}

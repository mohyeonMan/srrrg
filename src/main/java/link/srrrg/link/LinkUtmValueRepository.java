package link.srrrg.link;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkUtmValueRepository extends JpaRepository<LinkUtmValue, LinkUtmValue.LinkUtmValueId> {
	@EntityGraph(attributePaths = "field")
	List<LinkUtmValue> findByLinkId(Long linkId);
}

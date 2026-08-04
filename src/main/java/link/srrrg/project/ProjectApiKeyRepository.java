package link.srrrg.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApiKeyRepository extends JpaRepository<ProjectApiKey, Long> {
	List<ProjectApiKey> findByProjectIdOrderByCreatedAtDesc(Long projectId);
	Optional<ProjectApiKey> findByKeyHash(String keyHash);
}

package link.srrrg.project;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {
	Optional<ProjectInvitation> findByTokenHash(String tokenHash);
	List<ProjectInvitation> findByProjectId(Long projectId);
}

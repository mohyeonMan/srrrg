package link.srrrg.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDomainRepository extends JpaRepository<ProjectDomain, Long> {
	Optional<ProjectDomain> findByProjectId(Long projectId);
	Optional<ProjectDomain> findByIdAndProjectId(Long id, Long projectId);
}

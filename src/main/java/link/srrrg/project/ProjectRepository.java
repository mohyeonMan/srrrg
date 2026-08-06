package link.srrrg.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
	boolean existsBySlug(String slug);
}

package link.srrrg.link;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LinkRepository extends JpaRepository<Link, Long>, JpaSpecificationExecutor<Link> {

	@EntityGraph(attributePaths = "project")
	Optional<Link> findByCodeAndProjectIsNull(String code);
	@EntityGraph(attributePaths = "project")
	Optional<Link> findByDomainIdAndCode(Long domainId, String code);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "project")
	@Query("select l from Link l where l.code = :code and l.project is null")
	Optional<Link> lockAnonymousByCode(@Param("code") String code);
	Optional<Link> findByIdempotencyApiKeyIdAndIdempotencyKey(Long apiKeyId, String idempotencyKey);
	List<Link> findByProjectIdAndCampaignIsNullAndDeletedFalseOrderByIdDesc(Long projectId);
	List<Link> findByProjectIdAndCampaignIsNullAndDeletedFalseOrderByIdDesc(Long projectId, Pageable pageable);
	List<Link> findByProjectIdAndCampaignIsNullAndDeletedFalseAndIdLessThanOrderByIdDesc(Long projectId, Long id, Pageable pageable);

	List<Link> findByCampaignIdAndDeletedFalseOrderByIdDesc(Long campaignId, Pageable pageable);
	List<Link> findByCampaignIdAndDeletedFalseAndIdLessThanOrderByIdDesc(Long campaignId, Long id, Pageable pageable);
	Optional<Link> findByCampaignIdAndExternalId(Long campaignId, String externalId);

	@Modifying
	@Query("update Link l set l.deleted = true, l.updatedAt = CURRENT_TIMESTAMP where l.campaign.id = :campaignId and l.deleted = false")
	int softDeleteByCampaignId(@Param("campaignId") Long campaignId);

	@Modifying
	@Query("update Link l set l.accessCount = l.accessCount + 1 where l.id = :id")
	int incrementAccessCountById(@Param("id") Long id);

	@Modifying
	@Query("""
			update Link l
			   set l.accessCount = l.accessCount + 1,
			       l.redirectCount = l.redirectCount + 1
			 where l.id = :id
			""")
	int incrementAccessAndRedirectCountsById(@Param("id") Long id);

}

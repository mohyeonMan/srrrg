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

/**
 * Link는 {@code @SoftDelete}가 걸려 있어 삭제된 링크는 모든 조회에서 자동으로 빠진다.
 * 아래 메서드에 삭제 조건을 따로 적지 않는 이유다.
 */
public interface LinkRepository extends JpaRepository<Link, Long>, JpaSpecificationExecutor<Link> {

	@EntityGraph(attributePaths = {"project", "campaign"})
	Optional<Link> findByCodeAndProjectIsNull(String code);
	@EntityGraph(attributePaths = {"project", "campaign"})
	Optional<Link> findBySubdomainIsNullAndCode(String code);
	@EntityGraph(attributePaths = {"project", "campaign"})
	Optional<Link> findBySubdomainAndCode(String subdomain, String code);
	/**
	 * 익명 링크를 행 잠금으로 조회한다. 프로젝트 편입처럼 링크의 소유권을 바꾸는 처리에서
	 * 두 요청이 같은 링크를 동시에 가져가지 못하게 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "project")
	@Query("select l from Link l where l.code = :code and l.project is null")
	Optional<Link> lockAnonymousByCode(@Param("code") String code);
	// 멱등 키 조회. API key 단위로 구분하므로 다른 프로젝트가 같은 키 문자열을 써도 서로 간섭하지 않는다.
	Optional<Link> findByIdempotencyApiKeyIdAndIdempotencyKey(Long apiKeyId, String idempotencyKey);
	List<Link> findByProjectIdAndCampaignIsNullOrderByIdDesc(Long projectId);
	List<Link> findByProjectIdAndCampaignIsNullOrderByIdDesc(Long projectId, Pageable pageable);
	List<Link> findByProjectIdAndCampaignIsNullAndIdLessThanOrderByIdDesc(Long projectId, Long id, Pageable pageable);

	List<Link> findByCampaignIdOrderByIdDesc(Long campaignId, Pageable pageable);
	List<Link> findByCampaignIdAndIdLessThanOrderByIdDesc(Long campaignId, Long id, Pageable pageable);
	@EntityGraph(attributePaths = {"project", "campaign"})
	Optional<Link> findByProjectIdAndCode(Long projectId, String code);

	// @SoftDelete 엔티티의 bulk delete는 Hibernate가 soft delete UPDATE로 번역한다.
	// 이미 삭제된 링크는 자동으로 제외되므로 조건을 따로 걸지 않는다.
	@Modifying
	@Query("delete from Link l where l.campaign.id = :campaignId")
	int softDeleteByCampaignId(@Param("campaignId") Long campaignId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Link l where l.campaign.id = :campaignId and l.code in :codes")
	int softDeleteByCampaignIdAndCodeIn(@Param("campaignId") Long campaignId, @Param("codes") List<String> codes);

}

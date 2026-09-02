package link.srrrg.link;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 링크별 UTM 값을 다룬다. 핵심은 아래 두 네이티브 쿼리이며, 링크에 저장된 값과 캠페인 기본값을
 * 합쳐 지금 이 순간의 유효값을 계산한다.
 *
 * <p>우선순위 계산을 애플리케이션이 아니라 쿼리에 둔 것은 의도된 선택이다. 리다이렉트마다
 * 템플릿 필드·링크 값·캠페인 기본값을 각각 읽어 조합하면 왕복이 늘고, 세 값이 서로 다른 시점의
 * 상태일 수 있다.</p>
 *
 * <p>{@code COALESCE}가 우선순위 그 자체다. 링크에 값이 있으면 그것을, 없으면 캠페인 기본값을 쓴다.
 * 둘 다 없는 필드는 결과에서 빠져 최종 URL에도 실리지 않는다.</p>
 *
 * <p>삭제된 템플릿 필드를 조건에서 제외하므로, 필드를 지우면 그 UTM은 이후 리다이렉트부터 사라진다.</p>
 */
public interface LinkUtmValueRepository extends JpaRepository<LinkUtmValue, LinkUtmValue.LinkUtmValueId> {
	List<LinkUtmValue> findByLinkId(Long linkId);

	@Query(value = """
			SELECT field.name AS "fieldName", COALESCE(link_value.value, campaign_default.default_value) AS "value"
			  FROM links link
			  JOIN campaigns campaign ON campaign.id = link.campaign_id
			  JOIN utm_template_fields field ON field.utm_template_id = campaign.utm_template_id AND field.deleted_at IS NULL
			  LEFT JOIN link_utm_values link_value ON link_value.link_id = link.id AND link_value.field_name = field.name
			  LEFT JOIN campaign_utm_defaults campaign_default
			    ON campaign_default.campaign_id = campaign.id AND campaign_default.field_name = field.name
			 WHERE link.id = :linkId AND COALESCE(link_value.value, campaign_default.default_value) IS NOT NULL
			 ORDER BY field.name
			""", nativeQuery = true)
	List<EffectiveUtmValue> findEffectiveByLinkId(@Param("linkId") Long linkId);

	@Query(value = """
			SELECT link.id AS "linkId",
			       field.name AS "fieldName",
			       COALESCE(link_value.value, campaign_default.default_value) AS "value",
			       CASE WHEN link_value.value IS NOT NULL THEN 'LINK' ELSE 'CAMPAIGN_DEFAULT' END AS "source"
			  FROM links link
			  JOIN campaigns campaign ON campaign.id = link.campaign_id
			  JOIN utm_template_fields field ON field.utm_template_id = campaign.utm_template_id AND field.deleted_at IS NULL
			  LEFT JOIN link_utm_values link_value ON link_value.link_id = link.id AND link_value.field_name = field.name
			  LEFT JOIN campaign_utm_defaults campaign_default
			    ON campaign_default.campaign_id = campaign.id AND campaign_default.field_name = field.name
			 WHERE link.id IN (:linkIds) AND COALESCE(link_value.value, campaign_default.default_value) IS NOT NULL
			 ORDER BY link.id, field.name
			""", nativeQuery = true)
	// 목록 화면용. 링크마다 위 쿼리를 부르면 N+1이 되므로 한 번에 읽고, 값의 출처까지 함께 돌려줘
	// 화면에서 링크 고유값과 캠페인 상속값을 구분해 보여줄 수 있게 한다.
	List<EffectiveUtmValueByLink> findEffectiveByLinkIds(@Param("linkIds") List<Long> linkIds);

	interface EffectiveUtmValue {
		String getFieldName();
		String getValue();
	}

	interface EffectiveUtmValueByLink extends EffectiveUtmValue {
		Long getLinkId();
		String getSource();
	}
}

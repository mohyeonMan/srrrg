package link.srrrg.link;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

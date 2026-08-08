package link.srrrg.link;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkUtmValueRepository extends JpaRepository<LinkUtmValue, LinkUtmValue.LinkUtmValueId> {
	@EntityGraph(attributePaths = "field")
	List<LinkUtmValue> findByLinkId(Long linkId);

	@Query(value = """
			SELECT COALESCE(explicit.field_name, defaults.field_name) AS "fieldName",
			       COALESCE(explicit.value, defaults.value) AS "value"
			  FROM (
			        SELECT field.name AS field_name, link_value.value
			          FROM link_utm_values link_value
			          JOIN utm_template_fields field ON field.id = link_value.utm_template_field_id
			         WHERE link_value.link_id = :linkId
			       ) explicit
			  FULL OUTER JOIN (
			        SELECT field.name AS field_name, campaign_default.default_value AS value
			          FROM campaign_utm_defaults campaign_default
			          JOIN utm_template_fields field ON field.id = campaign_default.utm_template_field_id
			         WHERE campaign_default.campaign_id = (SELECT campaign_id FROM links WHERE id = :linkId)
			       ) defaults ON defaults.field_name = explicit.field_name
			 ORDER BY COALESCE(explicit.field_name, defaults.field_name)
			""", nativeQuery = true)
	List<EffectiveUtmValue> findEffectiveByLinkId(@Param("linkId") Long linkId);

	interface EffectiveUtmValue {
		String getFieldName();
		String getValue();
	}
}

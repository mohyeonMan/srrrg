package link.srrrg.statistics;

import link.srrrg.statistics.StatisticsResponse.Scope;

/**
 * 통계를 낼 대상 범위. 링크 하나, 캠페인, 프로젝트 중 하나이며 집계 쿼리의 WHERE 절을 결정한다.
 */
record StatisticsQueryScope(Scope type, long id) {
	/**
	 * 범위에 해당하는 SQL 조건을 돌려준다. 이 문자열이 집계 쿼리에 그대로 끼워 넣어지므로
	 * 여기에는 코드에 고정된 값만 있어야 하고, 식별자는 물음표 자리로 남겨 바인딩한다.
	 *
	 * <p>세 경우 모두 {@code deleted_at IS NULL}을 포함한다. 네이티브 SQL에는 soft delete 필터가
	 * 자동으로 붙지 않아, 여기서 빠뜨리면 삭제된 링크의 접근이 집계에 섞인다.</p>
	 */
	String predicate() {
		return switch (type) {
			case LINK -> "l.id = ? AND l.deleted_at IS NULL";
			case CAMPAIGN -> "l.campaign_id = ? AND l.deleted_at IS NULL";
			case PROJECT -> "l.project_id = ? AND l.deleted_at IS NULL";
		};
	}
}

package link.srrrg.statistics;

import link.srrrg.statistics.StatisticsResponse.Scope;

record StatisticsQueryScope(Scope type, long id) {
	String predicate() {
		return switch (type) {
			case LINK -> "l.id = ? AND l.deleted_at IS NULL";
			case CAMPAIGN -> "l.campaign_id = ? AND l.deleted_at IS NULL";
			case PROJECT -> "l.project_id = ? AND l.deleted_at IS NULL";
		};
	}
}

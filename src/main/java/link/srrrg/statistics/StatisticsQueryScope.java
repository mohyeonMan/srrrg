package link.srrrg.statistics;

import link.srrrg.statistics.StatisticsResponse.Scope;

record StatisticsQueryScope(Scope type, long id) {
	String predicate() {
		return switch (type) {
			case LINK -> "l.id = ? AND NOT l.is_deleted";
			case CAMPAIGN -> "l.campaign_id = ? AND NOT l.is_deleted";
			case PROJECT -> "l.project_id = ? AND NOT l.is_deleted";
		};
	}
}

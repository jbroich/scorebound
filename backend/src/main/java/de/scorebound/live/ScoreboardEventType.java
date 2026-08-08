package de.scorebound.live;

public enum ScoreboardEventType {

	SCORE_CREATED("score-created"),
	SCORE_CANCELLED("score-cancelled"),
	PERIOD_CHANGED("period-changed"),
	PARTICIPATION_CHANGED("participation-changed");

	private final String apiValue;

	ScoreboardEventType(String apiValue) {
		this.apiValue = apiValue;
	}

	public String apiValue() {
		return apiValue;
	}
}

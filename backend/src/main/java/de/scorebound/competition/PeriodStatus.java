package de.scorebound.competition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PeriodStatus {
	SCHEDULED,
	ACTIVE,
	CLOSED;

	@JsonCreator
	public static PeriodStatus fromApiValue(String value) {
		return valueOf(value.toUpperCase());
	}

	@JsonValue
	public String toApiValue() {
		return name().charAt(0) + name().substring(1).toLowerCase();
	}
}

package de.scorebound.scoring;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ScoreKind {
	CREDIT(1),
	DEBIT(-1);

	private final int direction;

	ScoreKind(int direction) {
		this.direction = direction;
	}

	public int apply(int amount) {
		return direction * amount;
	}

	@JsonCreator
	public static ScoreKind fromApiValue(String value) {
		return valueOf(value.toUpperCase());
	}

	@JsonValue
	public String toApiValue() {
		return name().charAt(0) + name().substring(1).toLowerCase();
	}
}

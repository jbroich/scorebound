package de.scorebound.display;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DisplayMode {
	FIXED("Fixed"),
	ROTATION("Rotation");

	private final String apiValue;

	DisplayMode(String apiValue) {
		this.apiValue = apiValue;
	}

	@JsonValue
	public String apiValue() {
		return apiValue;
	}

	@JsonCreator
	public static DisplayMode fromApiValue(String value) {
		for (DisplayMode mode : values()) {
			if (mode.apiValue.equalsIgnoreCase(value)) return mode;
		}
		throw new IllegalArgumentException("Unknown display mode: " + value);
	}
}

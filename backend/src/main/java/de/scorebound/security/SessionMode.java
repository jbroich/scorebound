package de.scorebound.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SessionMode {
	NORMAL("Normal"),
	DISPLAY("Display");

	private final String apiName;

	SessionMode(String apiName) {
		this.apiName = apiName;
	}

	@JsonValue
	public String apiName() {
		return apiName;
	}

	@JsonCreator
	public static SessionMode fromApiName(String value) {
		if (value == null || value.equalsIgnoreCase("Normal")) {
			return NORMAL;
		}
		if (value.equalsIgnoreCase("Display")) {
			return DISPLAY;
		}
		throw new IllegalArgumentException("Unknown session mode: " + value);
	}
}

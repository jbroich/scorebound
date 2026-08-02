package de.scorebound.identity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Role {
	ADMIN("Admin"),
	SCORER("Scorer"),
	MEMBER("Member"),
	DISPLAY("Display");

	private final String apiName;

	Role(String apiName) {
		this.apiName = apiName;
	}

	@JsonValue
	public String apiName() {
		return apiName;
	}

	@JsonCreator
	public static Role fromApiName(String value) {
		return Arrays.stream(values())
				.filter(role -> role.apiName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown role: " + value));
	}
}

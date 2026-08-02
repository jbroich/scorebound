package de.scorebound.security;

import java.time.Duration;

public final class SessionPolicy {

	public static final Duration NORMAL_DURATION = Duration.ofHours(12);
	public static final Duration DISPLAY_DURATION = Duration.ofDays(30);
	public static final String MODE_ATTRIBUTE = "scorebound.session.mode";
	public static final String EXPIRES_AT_ATTRIBUTE = "scorebound.session.expires-at";

	private SessionPolicy() {
	}
}

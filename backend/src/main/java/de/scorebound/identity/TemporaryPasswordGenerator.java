package de.scorebound.identity;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TemporaryPasswordGenerator {

	private static final char[] CHARACTERS =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
	private static final int LENGTH = 20;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		char[] password = new char[LENGTH];
		for (int index = 0; index < password.length; index++) {
			password[index] = CHARACTERS[secureRandom.nextInt(CHARACTERS.length)];
		}
		return new String(password);
	}
}

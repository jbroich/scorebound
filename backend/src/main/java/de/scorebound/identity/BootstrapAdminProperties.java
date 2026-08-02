package de.scorebound.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scorebound.bootstrap.admin")
public record BootstrapAdminProperties(String username, String password) {

	public boolean isConfigured() {
		return username != null && !username.isBlank() && password != null && !password.isBlank();
	}

	public boolean isPartiallyConfigured() {
		return (username != null && !username.isBlank()) != (password != null && !password.isBlank());
	}
}

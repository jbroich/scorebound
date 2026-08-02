package de.scorebound.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

	private final BootstrapAdminProperties properties;
	private final AccountService accountService;

	public BootstrapAdminInitializer(BootstrapAdminProperties properties, AccountService accountService) {
		this.properties = properties;
		this.accountService = accountService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (properties.isPartiallyConfigured()) {
			throw new IllegalStateException("Bootstrap admin username and password must be configured together");
		}
		if (properties.isConfigured()) {
			accountService.createBootstrapAdmin(properties.username(), properties.password());
		}
	}
}

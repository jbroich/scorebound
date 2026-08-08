package de.scorebound.display;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisplayConfigurationRepository
		extends JpaRepository<DisplayConfiguration, UUID> {
}

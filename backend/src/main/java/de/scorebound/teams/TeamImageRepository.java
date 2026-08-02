package de.scorebound.teams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeamImageRepository extends JpaRepository<TeamImage, UUID> {
}

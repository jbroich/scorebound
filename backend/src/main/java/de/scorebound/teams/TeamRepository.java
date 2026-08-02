package de.scorebound.teams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

	List<Team> findByActiveTrueOrderByNameAsc();

	boolean existsByActiveNameKeyAndIdNot(String activeNameKey, UUID id);

	boolean existsByActiveShortNameKeyAndIdNot(String activeShortNameKey, UUID id);
}

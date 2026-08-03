package de.scorebound.competition;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreboardRepository extends JpaRepository<Scoreboard, UUID> {

	List<Scoreboard> findByActiveTrueOrderByNameAsc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select scoreboard from Scoreboard scoreboard where scoreboard.id = :scoreboardId")
	Optional<Scoreboard> findByIdForUpdate(@Param("scoreboardId") UUID scoreboardId);
}

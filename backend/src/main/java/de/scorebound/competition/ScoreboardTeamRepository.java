package de.scorebound.competition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ScoreboardTeamRepository extends JpaRepository<ScoreboardTeam, ScoreboardTeam.Key> {

	List<ScoreboardTeam> findByScoreboardIdOrderByPositionAsc(UUID scoreboardId);

	boolean existsByScoreboardIdAndTeamId(UUID scoreboardId, UUID teamId);

	void deleteByScoreboardIdAndTeamId(UUID scoreboardId, UUID teamId);

	@Query("select coalesce(max(selection.position), -1) from ScoreboardTeam selection "
			+ "where selection.scoreboardId = :scoreboardId")
	int maximumPosition(@Param("scoreboardId") UUID scoreboardId);
}

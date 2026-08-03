package de.scorebound.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scoreboard_teams")
@IdClass(ScoreboardTeam.Key.class)
public class ScoreboardTeam {

	@Id
	@Column(name = "scoreboard_id")
	private UUID scoreboardId;

	@Id
	@Column(name = "team_id")
	private UUID teamId;

	@Column(nullable = false)
	private int position;

	@Column(name = "selected_at", nullable = false)
	private Instant selectedAt;

	@Column(name = "selected_by")
	private UUID selectedBy;

	protected ScoreboardTeam() {
	}

	private ScoreboardTeam(UUID scoreboardId, UUID teamId, int position, UUID actorId) {
		this.scoreboardId = scoreboardId;
		this.teamId = teamId;
		this.position = position;
		this.selectedAt = Instant.now();
		this.selectedBy = actorId;
	}

	public static ScoreboardTeam create(UUID scoreboardId, UUID teamId, int position, UUID actorId) {
		return new ScoreboardTeam(scoreboardId, teamId, position, actorId);
	}

	public UUID getScoreboardId() { return scoreboardId; }
	public UUID getTeamId() { return teamId; }
	public int getPosition() { return position; }

	public record Key(UUID scoreboardId, UUID teamId) implements Serializable {
	}
}

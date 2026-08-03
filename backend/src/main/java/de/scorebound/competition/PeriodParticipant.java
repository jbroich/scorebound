package de.scorebound.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "period_participants")
public class PeriodParticipant {

	@Id
	private UUID id;

	@Column(name = "period_id", nullable = false)
	private UUID periodId;

	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@Column(nullable = false)
	private int position;

	@Column(name = "current_score", nullable = false)
	private int currentScore;

	@Column(nullable = false)
	private boolean winner;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "joined_by")
	private UUID joinedBy;

	@Version
	@Column(nullable = false)
	private long version;

	protected PeriodParticipant() {
	}

	private PeriodParticipant(UUID periodId, UUID teamId, int position, UUID actorId) {
		this.id = UUID.randomUUID();
		this.periodId = periodId;
		this.teamId = teamId;
		this.position = position;
		this.currentScore = 0;
		this.winner = false;
		this.joinedAt = Instant.now();
		this.joinedBy = actorId;
	}

	public static PeriodParticipant create(UUID periodId, UUID teamId, int position, UUID actorId) {
		return new PeriodParticipant(periodId, teamId, position, actorId);
	}

	public void markWinner(boolean winner) { this.winner = winner; }

	public UUID getId() { return id; }
	public UUID getPeriodId() { return periodId; }
	public UUID getTeamId() { return teamId; }
	public int getPosition() { return position; }
	public int getCurrentScore() { return currentScore; }
	public boolean isWinner() { return winner; }
}

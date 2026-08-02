package de.scorebound.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scorer_assignments")
@IdClass(ScorerAssignment.Key.class)
public class ScorerAssignment {

	@Id
	@Column(name = "account_id")
	private UUID accountId;

	@Id
	@Column(name = "scoreboard_id")
	private UUID scoreboardId;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	@Column(name = "assigned_by", nullable = false)
	private UUID assignedBy;

	protected ScorerAssignment() {
	}

	private ScorerAssignment(UUID accountId, UUID scoreboardId, UUID actorId) {
		this.accountId = accountId;
		this.scoreboardId = scoreboardId;
		this.assignedAt = Instant.now();
		this.assignedBy = actorId;
	}

	public static ScorerAssignment create(UUID accountId, UUID scoreboardId, UUID actorId) {
		return new ScorerAssignment(accountId, scoreboardId, actorId);
	}

	public UUID getAccountId() { return accountId; }
	public UUID getScoreboardId() { return scoreboardId; }

	public record Key(UUID accountId, UUID scoreboardId) implements Serializable {
	}
}

package de.scorebound.display;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "display_assignments")
@IdClass(DisplayAssignment.Key.class)
public class DisplayAssignment {
	@Id @Column(name = "account_id") private UUID accountId;
	@Id @Column(name = "scoreboard_id") private UUID scoreboardId;
	@Column(nullable = false) private int position;
	@Column(name = "assigned_at", nullable = false) private Instant assignedAt;
	@Column(name = "assigned_by", nullable = false) private UUID assignedBy;

	protected DisplayAssignment() {
	}

	private DisplayAssignment(UUID accountId, UUID scoreboardId, int position, UUID actorId) {
		this.accountId = accountId;
		this.scoreboardId = scoreboardId;
		this.position = position;
		this.assignedAt = Instant.now();
		this.assignedBy = actorId;
	}

	public static DisplayAssignment create(UUID accountId, UUID scoreboardId, int position,
			UUID actorId) {
		return new DisplayAssignment(accountId, scoreboardId, position, actorId);
	}

	public UUID getAccountId() { return accountId; }
	public UUID getScoreboardId() { return scoreboardId; }
	public int getPosition() { return position; }

	public record Key(UUID accountId, UUID scoreboardId) implements Serializable {
	}
}

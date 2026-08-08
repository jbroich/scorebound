package de.scorebound.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "competition_periods")
public class CompetitionPeriod {

	@Id
	private UUID id;

	@Column(name = "scoreboard_id", nullable = false)
	private UUID scoreboardId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PeriodStatus status;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "closed_by")
	private UUID closedBy;

	@Column(name = "reopened_at")
	private Instant reopenedAt;

	@Column(name = "reopened_by")
	private UUID reopenedBy;

	@Column(name = "visual_ceiling", nullable = false)
	private int visualCeiling;

	@Column(name = "active_scoreboard_key", unique = true)
	private UUID activeScoreboardKey;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by")
	private UUID createdBy;

	protected CompetitionPeriod() {
	}

	private CompetitionPeriod(UUID scoreboardId, String name, Instant startsAt,
			Instant endsAt, UUID actorId) {
		this.id = UUID.randomUUID();
		this.scoreboardId = scoreboardId;
		this.name = name;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.status = PeriodStatus.SCHEDULED;
		this.visualCeiling = 0;
		this.createdAt = Instant.now();
		this.createdBy = actorId;
	}

	public static CompetitionPeriod schedule(UUID scoreboardId, String name, Instant startsAt,
			Instant endsAt, UUID actorId) {
		return new CompetitionPeriod(scoreboardId, name, startsAt, endsAt, actorId);
	}

	public void activate() {
		if (status != PeriodStatus.SCHEDULED) {
			throw new IllegalStateException("Only scheduled periods can be activated");
		}
		status = PeriodStatus.ACTIVE;
		activeScoreboardKey = scoreboardId;
		visualCeiling = Math.max(visualCeiling, 100);
	}

	public void includeScore(long score) {
		if (score <= 0 || visualCeiling == Integer.MAX_VALUE) {
			return;
		}
		long target = Math.max(100, score + Math.max(25, (score + 4) / 5));
		long magnitude = 10;
		while (magnitude <= target / 10 && magnitude <= 100_000_000) {
			magnitude *= 10;
		}
		long[] steps = { magnitude, magnitude * 2, magnitude * 5, magnitude * 10 };
		for (long step : steps) {
			if (step >= target) {
				visualCeiling = (int) Math.max(visualCeiling,
						Math.min(step, Integer.MAX_VALUE));
				return;
			}
		}
		visualCeiling = Integer.MAX_VALUE;
	}

	public void close(Instant closedAt, UUID actorId) {
		if (status != PeriodStatus.ACTIVE) {
			throw new IllegalStateException("Only active periods can be closed");
		}
		status = PeriodStatus.CLOSED;
		activeScoreboardKey = null;
		this.closedAt = closedAt;
		this.closedBy = actorId;
	}

	public void reopen(Instant reopenedAt, UUID actorId) {
		if (status != PeriodStatus.CLOSED) {
			throw new IllegalStateException("Only closed periods can be reopened");
		}
		status = PeriodStatus.ACTIVE;
		activeScoreboardKey = scoreboardId;
		closedAt = null;
		closedBy = null;
		this.reopenedAt = reopenedAt;
		this.reopenedBy = actorId;
	}

	public UUID getId() { return id; }
	public UUID getScoreboardId() { return scoreboardId; }
	public String getName() { return name; }
	public Instant getStartsAt() { return startsAt; }
	public Instant getEndsAt() { return endsAt; }
	public PeriodStatus getStatus() { return status; }
	public Instant getClosedAt() { return closedAt; }
	public UUID getClosedBy() { return closedBy; }
	public Instant getReopenedAt() { return reopenedAt; }
	public int getVisualCeiling() { return visualCeiling; }
}

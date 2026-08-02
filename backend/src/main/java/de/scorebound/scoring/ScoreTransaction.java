package de.scorebound.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_transactions")
public class ScoreTransaction {

	@Id
	private UUID id;

	@Column(name = "period_id", nullable = false)
	private UUID periodId;

	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@Column(name = "member_id")
	private UUID memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private ScoreKind kind;

	@Column(nullable = false)
	private int amount;

	@Column(name = "resulting_score", nullable = false)
	private long resultingScore;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "idempotency_key", length = 128)
	private String idempotencyKey;

	protected ScoreTransaction() {
	}

	private ScoreTransaction(UUID periodId, UUID teamId, UUID memberId, ScoreKind kind,
			int amount, long resultingScore, String reason, UUID actorId, String idempotencyKey) {
		this.id = UUID.randomUUID();
		this.periodId = periodId;
		this.teamId = teamId;
		this.memberId = memberId;
		this.kind = kind;
		this.amount = amount;
		this.resultingScore = resultingScore;
		this.reason = reason;
		this.createdAt = Instant.now();
		this.createdBy = actorId;
		this.idempotencyKey = idempotencyKey;
	}

	public static ScoreTransaction create(UUID periodId, UUID teamId, UUID memberId,
			ScoreKind kind, int amount, long resultingScore, String reason, UUID actorId,
			String idempotencyKey) {
		return new ScoreTransaction(periodId, teamId, memberId, kind, amount, resultingScore, reason,
				actorId, idempotencyKey);
	}

	public boolean matches(UUID requestedTeamId, UUID requestedMemberId,
			ScoreKind kind, int amount, String reason) {
		UUID directTeamId = memberId == null ? teamId : null;
		return java.util.Objects.equals(directTeamId, requestedTeamId)
				&& java.util.Objects.equals(memberId, requestedMemberId) && this.kind == kind
				&& this.amount == amount && this.reason.equals(reason);
	}

	public UUID getId() { return id; }
	public UUID getPeriodId() { return periodId; }
	public UUID getTeamId() { return teamId; }
	public UUID getMemberId() { return memberId; }
	public ScoreKind getKind() { return kind; }
	public int getAmount() { return amount; }
	public long getResultingScore() { return resultingScore; }
	public String getReason() { return reason; }
	public Instant getCreatedAt() { return createdAt; }
	public UUID getCreatedBy() { return createdBy; }
	public String getIdempotencyKey() { return idempotencyKey; }
}

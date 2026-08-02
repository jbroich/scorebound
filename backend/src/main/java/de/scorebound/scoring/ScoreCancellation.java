package de.scorebound.scoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_cancellations")
public class ScoreCancellation {

	@Id
	@Column(name = "transaction_id")
	private UUID transactionId;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	protected ScoreCancellation() {
	}

	private ScoreCancellation(UUID transactionId, String reason, UUID actorId) {
		this.transactionId = transactionId;
		this.reason = reason;
		this.createdAt = Instant.now();
		this.createdBy = actorId;
	}

	public static ScoreCancellation create(UUID transactionId, String reason, UUID actorId) {
		return new ScoreCancellation(transactionId, reason, actorId);
	}

	public UUID getTransactionId() { return transactionId; }
	public String getReason() { return reason; }
	public Instant getCreatedAt() { return createdAt; }
	public UUID getCreatedBy() { return createdBy; }
}

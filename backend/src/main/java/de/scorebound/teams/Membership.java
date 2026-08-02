package de.scorebound.teams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class Membership {

	@Id
	private UUID id;

	@Column(name = "member_id", nullable = false)
	private UUID memberId;

	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@Column(name = "valid_from", nullable = false)
	private Instant validFrom;

	@Column(name = "valid_until")
	private Instant validUntil;

	@Column(name = "open_membership_key", unique = true)
	private UUID openMembershipKey;

	@Column(name = "created_by")
	private UUID createdBy;

	protected Membership() {
	}

	private Membership(UUID memberId, UUID teamId, Instant validFrom, UUID actorId) {
		this.id = UUID.randomUUID();
		this.memberId = memberId;
		this.teamId = teamId;
		this.validFrom = validFrom;
		this.openMembershipKey = memberId;
		this.createdBy = actorId;
	}

	public static Membership open(UUID memberId, UUID teamId, Instant validFrom, UUID actorId) {
		return new Membership(memberId, teamId, validFrom, actorId);
	}

	public void close(Instant validUntil) {
		if (!validUntil.isAfter(validFrom)) {
			throw new IllegalArgumentException("Team change must be after the current membership began");
		}
		this.validUntil = validUntil;
		this.openMembershipKey = null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMemberId() {
		return memberId;
	}

	public UUID getTeamId() {
		return teamId;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public Instant getValidUntil() {
		return validUntil;
	}
}

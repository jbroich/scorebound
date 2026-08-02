package de.scorebound.teams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "members")
public class Member {

	@Id
	private UUID id;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "first_name", length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Column(nullable = false)
	private boolean active;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "modified_at", nullable = false)
	private Instant modifiedAt;

	@Column(name = "modified_by")
	private UUID modifiedBy;

	protected Member() {
	}

	private Member(String displayName, String firstName, String lastName, UUID actorId) {
		this.id = UUID.randomUUID();
		this.displayName = displayName;
		this.firstName = firstName;
		this.lastName = lastName;
		this.active = true;
		Instant now = Instant.now();
		this.createdAt = now;
		this.createdBy = actorId;
		this.modifiedAt = now;
		this.modifiedBy = actorId;
	}

	public static Member create(String displayName, String firstName, String lastName, UUID actorId) {
		return new Member(displayName, firstName, lastName, actorId);
	}

	public void update(String displayName, String firstName, String lastName,
			Boolean active, UUID actorId) {
		if (displayName != null) {
			this.displayName = displayName;
		}
		if (firstName != null) {
			this.firstName = firstName.isBlank() ? null : firstName;
		}
		if (lastName != null) {
			this.lastName = lastName.isBlank() ? null : lastName;
		}
		this.active = active == null ? this.active : active;
		this.modifiedAt = Instant.now();
		this.modifiedBy = actorId;
	}

	public UUID getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public boolean isActive() {
		return active;
	}
}

package de.scorebound.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scoreboards")
public class Scoreboard {

	@Id
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 500)
	private String description;

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

	protected Scoreboard() {
	}

	private Scoreboard(String name, String description, UUID actorId) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.description = description;
		this.active = true;
		Instant now = Instant.now();
		this.createdAt = now;
		this.createdBy = actorId;
		this.modifiedAt = now;
		this.modifiedBy = actorId;
	}

	public static Scoreboard create(String name, String description, UUID actorId) {
		return new Scoreboard(name, description, actorId);
	}

	public void update(String name, String description, Boolean active, UUID actorId) {
		if (name != null) {
			this.name = name;
		}
		if (description != null) {
			this.description = description.isBlank() ? null : description;
		}
		if (active != null) {
			this.active = active;
		}
		this.modifiedAt = Instant.now();
		this.modifiedBy = actorId;
	}

	public UUID getId() { return id; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public boolean isActive() { return active; }
}

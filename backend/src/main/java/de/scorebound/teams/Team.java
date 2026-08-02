package de.scorebound.teams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team {

	@Id
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "normalized_name", nullable = false, length = 100)
	private String normalizedName;

	@Column(name = "active_name_key", unique = true, length = 100)
	private String activeNameKey;

	@Column(name = "short_name", nullable = false, length = 20)
	private String shortName;

	@Column(name = "normalized_short_name", nullable = false, length = 20)
	private String normalizedShortName;

	@Column(name = "active_short_name_key", unique = true, length = 20)
	private String activeShortNameKey;

	@Column(nullable = false, length = 7)
	private String color;

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

	protected Team() {
	}

	private Team(String name, String shortName, String color, UUID actorId) {
		this.id = UUID.randomUUID();
		this.active = true;
		setPresentation(name, shortName, color);
		Instant now = Instant.now();
		this.createdAt = now;
		this.createdBy = actorId;
		this.modifiedAt = now;
		this.modifiedBy = actorId;
	}

	public static Team create(String name, String shortName, String color, UUID actorId) {
		return new Team(name, shortName, color, actorId);
	}

	public void update(String name, String shortName, String color, Boolean active, UUID actorId) {
		setPresentation(name == null ? this.name : name,
				shortName == null ? this.shortName : shortName,
				color == null ? this.color : color);
		if (active != null) {
			this.active = active;
			refreshActiveKeys();
		}
		this.modifiedAt = Instant.now();
		this.modifiedBy = actorId;
	}

	private void setPresentation(String name, String shortName, String color) {
		this.name = name;
		this.normalizedName = normalize(name);
		this.shortName = shortName;
		this.normalizedShortName = normalize(shortName);
		this.color = color;
		refreshActiveKeys();
	}

	private void refreshActiveKeys() {
		this.activeNameKey = active ? normalizedName : null;
		this.activeShortNameKey = active ? normalizedShortName : null;
	}

	public static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getShortName() {
		return shortName;
	}

	public String getColor() {
		return color;
	}

	public boolean isActive() {
		return active;
	}
}

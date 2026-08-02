package de.scorebound.teams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "team_images")
public class TeamImage {

	@Id
	@Column(name = "team_id")
	private UUID teamId;

	@Column(name = "content_type", nullable = false, length = 32)
	private String contentType;

	@Column(name = "image_data", nullable = false)
	private byte[] data;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by")
	private UUID updatedBy;

	protected TeamImage() {
	}

	private TeamImage(UUID teamId, String contentType, byte[] data, UUID actorId) {
		this.teamId = teamId;
		replace(contentType, data, actorId);
	}

	public static TeamImage create(UUID teamId, String contentType, byte[] data, UUID actorId) {
		return new TeamImage(teamId, contentType, data, actorId);
	}

	public void replace(String contentType, byte[] data, UUID actorId) {
		this.contentType = contentType;
		this.data = data.clone();
		this.updatedAt = Instant.now();
		this.updatedBy = actorId;
	}

	public String getContentType() {
		return contentType;
	}

	public byte[] getData() {
		return data.clone();
	}
}

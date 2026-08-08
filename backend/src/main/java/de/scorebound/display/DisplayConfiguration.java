package de.scorebound.display;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "display_configurations")
public class DisplayConfiguration {
	@Id @Column(name = "account_id") private UUID accountId;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private DisplayMode mode;
	@Column(name = "fixed_scoreboard_id") private UUID fixedScoreboardId;
	@Column(name = "rotation_seconds", nullable = false) private int rotationSeconds;
	@Column(name = "sound_enabled", nullable = false) private boolean soundEnabled;
	@Column(name = "modified_at", nullable = false) private Instant modifiedAt;

	protected DisplayConfiguration() {
	}

	private DisplayConfiguration(UUID accountId, UUID firstScoreboardId) {
		this.accountId = accountId;
		this.mode = DisplayMode.FIXED;
		this.fixedScoreboardId = firstScoreboardId;
		this.rotationSeconds = 30;
		this.soundEnabled = false;
		this.modifiedAt = Instant.now();
	}

	public static DisplayConfiguration defaults(UUID accountId, UUID firstScoreboardId) {
		return new DisplayConfiguration(accountId, firstScoreboardId);
	}

	public void update(DisplayMode mode, UUID fixedScoreboardId, int rotationSeconds,
			boolean soundEnabled) {
		this.mode = mode;
		this.fixedScoreboardId = fixedScoreboardId;
		this.rotationSeconds = rotationSeconds;
		this.soundEnabled = soundEnabled;
		this.modifiedAt = Instant.now();
	}

	public UUID getAccountId() { return accountId; }
	public DisplayMode getMode() { return mode; }
	public UUID getFixedScoreboardId() { return fixedScoreboardId; }
	public int getRotationSeconds() { return rotationSeconds; }
	public boolean isSoundEnabled() { return soundEnabled; }
}

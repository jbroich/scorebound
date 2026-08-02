package de.scorebound.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

	@Id
	private UUID id;

	@Column(nullable = false, length = 64)
	private String username;

	@Column(name = "normalized_username", nullable = false, unique = true, length = 64)
	private String normalizedUsername;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "member_id")
	private UUID memberId;

	@Column(name = "preferred_locale", length = 2)
	private String preferredLocale;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"))
	@Column(name = "role", nullable = false, length = 16)
	@Enumerated(EnumType.STRING)
	private Set<Role> roles = new LinkedHashSet<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "modified_at", nullable = false)
	private Instant modifiedAt;

	@Column(name = "modified_by")
	private UUID modifiedBy;

	protected Account() {
	}

	private Account(UUID id, String username, String normalizedUsername, String passwordHash,
			Set<Role> roles, String preferredLocale, Instant now, UUID actorId) {
		this.id = id;
		this.username = username;
		this.normalizedUsername = normalizedUsername;
		this.passwordHash = passwordHash;
		this.enabled = true;
		this.mustChangePassword = true;
		this.roles = new LinkedHashSet<>(roles);
		this.preferredLocale = preferredLocale;
		this.createdAt = now;
		this.createdBy = actorId;
		this.modifiedAt = now;
		this.modifiedBy = actorId;
	}

	public static Account create(String username, String normalizedUsername, String passwordHash,
			Set<Role> roles, String preferredLocale, UUID actorId) {
		return new Account(UUID.randomUUID(), username, normalizedUsername, passwordHash, roles,
				preferredLocale, Instant.now(), actorId);
	}

	public void replacePassword(String passwordHash, boolean mustChangePassword, UUID actorId) {
		this.passwordHash = passwordHash;
		this.mustChangePassword = mustChangePassword;
		this.modifiedAt = Instant.now();
		this.modifiedBy = actorId;
	}

	public void changeMemberLink(UUID memberId, UUID actorId) {
		this.memberId = memberId;
		this.modifiedAt = Instant.now();
		this.modifiedBy = actorId;
	}

	public UUID getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getNormalizedUsername() {
		return normalizedUsername;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public UUID getMemberId() {
		return memberId;
	}

	public String getPreferredLocale() {
		return preferredLocale;
	}

	public Set<Role> getRoles() {
		return Set.copyOf(roles);
	}
}

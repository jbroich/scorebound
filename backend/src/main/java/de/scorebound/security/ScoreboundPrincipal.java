package de.scorebound.security;

import de.scorebound.identity.Account;
import de.scorebound.identity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class ScoreboundPrincipal implements UserDetails {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID accountId;
	private final String username;
	private final String password;
	private final boolean enabled;
	private final boolean mustChangePassword;
	private final String preferredLocale;
	private final Set<Role> roles;

	private ScoreboundPrincipal(Account account) {
		this.accountId = account.getId();
		this.username = account.getUsername();
		this.password = account.getPasswordHash();
		this.enabled = account.isEnabled();
		this.mustChangePassword = account.isMustChangePassword();
		this.preferredLocale = account.getPreferredLocale();
		this.roles = account.getRoles();
	}

	public static ScoreboundPrincipal from(Account account) {
		return new ScoreboundPrincipal(account);
	}

	public UUID accountId() {
		return accountId;
	}

	public boolean mustChangePassword() {
		return mustChangePassword;
	}

	public String preferredLocale() {
		return preferredLocale;
	}

	public Set<Role> roles() {
		return roles;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return roles.stream()
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
				.toList();
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}

package de.scorebound.web;

import de.scorebound.identity.Account;
import de.scorebound.identity.AccountService;
import de.scorebound.identity.Role;
import de.scorebound.security.ScoreboundPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping
	public ResponseEntity<TemporaryAccountResponse> createAccount(
			@Valid @RequestBody CreateAccountRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		AccountService.CreatedAccount created = accountService.createWithTemporaryPassword(
				request.username(), request.roles(), request.preferredLocale(), actor.accountId());
		return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.account().getId()))
				.body(TemporaryAccountResponse.from(created.account(), created.temporaryPassword()));
	}

	@PostMapping("/{accountId}/temporary-password")
	public TemporaryPasswordResponse issueTemporaryPassword(@PathVariable UUID accountId,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return new TemporaryPasswordResponse(accountId.toString(),
				accountService.issueTemporaryPassword(accountId, actor.accountId()));
	}

	public record CreateAccountRequest(
			@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
			@NotEmpty Set<Role> roles,
			@Pattern(regexp = "en|de") String preferredLocale) {
	}

	public record TemporaryAccountResponse(String id, String username, Set<Role> roles,
			boolean enabled, boolean mustChangePassword, String preferredLocale,
			String temporaryPassword) {

		static TemporaryAccountResponse from(Account account, String temporaryPassword) {
			return new TemporaryAccountResponse(account.getId().toString(), account.getUsername(),
					account.getRoles(), account.isEnabled(), account.isMustChangePassword(),
					account.getPreferredLocale(), temporaryPassword);
		}
	}

	public record TemporaryPasswordResponse(String accountId, String temporaryPassword) {
	}
}

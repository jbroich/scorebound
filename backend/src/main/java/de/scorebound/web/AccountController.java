package de.scorebound.web;

import tools.jackson.databind.JsonNode;
import de.scorebound.identity.Account;
import de.scorebound.identity.AccountService;
import de.scorebound.identity.Role;
import de.scorebound.security.ScoreboundPrincipal;
import de.scorebound.scoring.ScoringService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {

	private final AccountService accountService;
	private final ScoringService scoringService;

	public AccountController(AccountService accountService, ScoringService scoringService) {
		this.accountService = accountService;
		this.scoringService = scoringService;
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

	@GetMapping
	public List<AccountResponse> listAccounts() {
		return accountService.listAccounts().stream().map(this::response).toList();
	}

	@GetMapping("/{accountId}")
	public AccountResponse getAccount(@PathVariable UUID accountId) {
		return response(accountService.requireAccount(accountId));
	}

	@PostMapping("/{accountId}/temporary-password")
	public TemporaryPasswordResponse issueTemporaryPassword(@PathVariable UUID accountId,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return new TemporaryPasswordResponse(accountId.toString(),
				accountService.issueTemporaryPassword(accountId, actor.accountId()));
	}

	@PatchMapping("/{accountId}")
	public AccountResponse updateAccount(@PathVariable UUID accountId,
			@RequestBody JsonNode request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		if (!request.isObject() || request.isEmpty()) {
			throw new IllegalArgumentException("At least one account field is required");
		}
		int knownFields = (request.has("enabled") ? 1 : 0) + (request.has("roles") ? 1 : 0)
				+ (request.has("preferredLocale") ? 1 : 0) + (request.has("memberId") ? 1 : 0);
		if (knownFields != request.size()) {
			throw new IllegalArgumentException("Unknown account field");
		}
		Boolean enabled = request.has("enabled") ? requireBoolean(request.get("enabled"), "enabled")
				: null;
		Set<Role> roles = request.has("roles") ? requireRoles(request.get("roles")) : null;
		String preferredLocale = request.has("preferredLocale")
				? nullableText(request.get("preferredLocale"), "preferredLocale") : null;
		UUID memberId = request.has("memberId") ? nullableUuid(request.get("memberId"), "memberId")
				: null;
		return response(accountService.updateAdministration(accountId, enabled, roles,
				preferredLocale, request.has("preferredLocale"), memberId, request.has("memberId"),
				actor.accountId()));
	}

	@GetMapping("/{accountId}/scorer-assignments")
	public List<UUID> listScorerAssignments(@PathVariable UUID accountId) {
		return scoringService.listScorerAssignments(accountId);
	}

	@PutMapping("/{accountId}/scorer-assignments/{scoreboardId}")
	public ResponseEntity<Void> assignScorer(@PathVariable UUID accountId,
			@PathVariable UUID scoreboardId, @AuthenticationPrincipal ScoreboundPrincipal actor) {
		scoringService.assignScorer(accountId, scoreboardId, actor.accountId());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{accountId}/scorer-assignments/{scoreboardId}")
	public ResponseEntity<Void> removeScorerAssignment(@PathVariable UUID accountId,
			@PathVariable UUID scoreboardId) {
		scoringService.removeScorerAssignment(accountId, scoreboardId);
		return ResponseEntity.noContent().build();
	}

	public record CreateAccountRequest(
			@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
			@NotEmpty Set<Role> roles,
			@Pattern(regexp = "en|de") String preferredLocale) {
	}

	public record TemporaryAccountResponse(String id, String username, String memberId, Set<Role> roles,
			boolean enabled, boolean mustChangePassword, String preferredLocale,
			String temporaryPassword) {

		static TemporaryAccountResponse from(Account account, String temporaryPassword) {
			return new TemporaryAccountResponse(account.getId().toString(), account.getUsername(),
					account.getMemberId() == null ? null : account.getMemberId().toString(),
					account.getRoles(), account.isEnabled(), account.isMustChangePassword(),
					account.getPreferredLocale(), temporaryPassword);
		}
	}

	public record TemporaryPasswordResponse(String accountId, String temporaryPassword) {
	}

	public record AccountResponse(String id, String username, String memberId, Set<Role> roles,
			boolean enabled, boolean mustChangePassword, String preferredLocale,
			List<UUID> scorerAssignments) {

	}

	private AccountResponse response(Account account) {
		return new AccountResponse(account.getId().toString(), account.getUsername(),
				account.getMemberId() == null ? null : account.getMemberId().toString(),
				account.getRoles(), account.isEnabled(), account.isMustChangePassword(),
				account.getPreferredLocale(), scoringService.listScorerAssignments(account.getId()));
	}

	private static Boolean requireBoolean(JsonNode node, String field) {
		if (!node.isBoolean()) {
			throw new IllegalArgumentException(field + " must be a boolean");
		}
		return node.asBoolean();
	}

	private static Set<Role> requireRoles(JsonNode node) {
		if (!node.isArray() || node.isEmpty()) {
			throw new IllegalArgumentException("roles must be a non-empty array");
		}
		java.util.LinkedHashSet<Role> roles = new java.util.LinkedHashSet<>();
		node.forEach(role -> roles.add(Role.fromApiName(role.asText())));
		return Set.copyOf(roles);
	}

	private static String nullableText(JsonNode node, String field) {
		if (node.isNull()) {
			return null;
		}
		if (!node.isString()) {
			throw new IllegalArgumentException(field + " must be a string or null");
		}
		return node.asText();
	}

	private static UUID nullableUuid(JsonNode node, String field) {
		if (node.isNull()) {
			return null;
		}
		try {
			return UUID.fromString(node.asText());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(field + " must be a UUID or null", exception);
		}
	}
}

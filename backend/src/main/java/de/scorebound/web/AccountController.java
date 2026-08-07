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
		if (!request.isObject() || request.size() != 1 || !request.has("memberId")) {
			throw new IllegalArgumentException("Only memberId can be changed by this endpoint yet");
		}
		JsonNode memberNode = request.get("memberId");
		UUID memberId;
		try {
			memberId = memberNode.isNull() ? null : UUID.fromString(memberNode.asText());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("memberId must be a UUID or null", exception);
		}
		return AccountResponse.from(accountService.changeMemberLink(accountId, memberId,
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
			boolean enabled, boolean mustChangePassword, String preferredLocale) {

		static AccountResponse from(Account account) {
			return new AccountResponse(account.getId().toString(), account.getUsername(),
					account.getMemberId() == null ? null : account.getMemberId().toString(),
					account.getRoles(), account.isEnabled(), account.isMustChangePassword(),
					account.getPreferredLocale());
		}
	}
}

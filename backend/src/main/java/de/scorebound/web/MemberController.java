package de.scorebound.web;

import de.scorebound.identity.Role;
import de.scorebound.security.ScoreboundPrincipal;
import de.scorebound.teams.Member;
import de.scorebound.teams.Membership;
import de.scorebound.teams.TeamMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

	private final TeamMemberService teamMemberService;

	public MemberController(TeamMemberService teamMemberService) {
		this.teamMemberService = teamMemberService;
	}

	@GetMapping
	public MemberPage listMembers(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "false") boolean includeInactive,
			@AuthenticationPrincipal ScoreboundPrincipal principal) {
		if (includeInactive && !principal.roles().contains(Role.ADMIN)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
					"Only administrators can include inactive members");
		}
		Page<Member> members = teamMemberService.listMembers(page, size, includeInactive);
		List<MemberResponse> content = members.getContent().stream()
				.map(member -> response(teamMemberService.getMemberDetails(member.getId())))
				.toList();
		return new MemberPage(content, members.getNumber(), members.getSize(),
				members.getTotalElements(), members.getTotalPages());
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody CreateMemberRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		TeamMemberService.CreatedMember created = teamMemberService.createMember(request.displayName(),
				request.firstName(), request.lastName(), request.teamId(), actor.accountId());
		MemberResponse response = response(new TeamMemberService.MemberDetails(created.member(),
				List.of(created.membership())));
		return ResponseEntity.created(URI.create("/api/v1/members/" + created.member().getId()))
				.body(response);
	}

	@GetMapping("/{memberId}")
	public MemberResponse getMember(@PathVariable UUID memberId) {
		return response(teamMemberService.getMemberDetails(memberId));
	}

	@PatchMapping("/{memberId}")
	@PreAuthorize("hasRole('ADMIN')")
	public MemberResponse updateMember(@PathVariable UUID memberId,
			@Valid @RequestBody UpdateMemberRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		teamMemberService.updateMember(memberId, request.displayName(), request.firstName(),
				request.lastName(), request.active(), actor.accountId());
		return response(teamMemberService.getMemberDetails(memberId));
	}

	@PostMapping("/{memberId}/team-changes")
	@PreAuthorize("hasRole('ADMIN')")
	public MemberResponse changeTeam(@PathVariable UUID memberId,
			@Valid @RequestBody TeamChangeRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		teamMemberService.changeTeam(memberId, request.teamId(), request.effectiveAt(), actor.accountId());
		return response(teamMemberService.getMemberDetails(memberId));
	}

	private MemberResponse response(TeamMemberService.MemberDetails details) {
		List<MembershipResponse> memberships = details.memberships().stream()
				.map(this::membershipResponse)
				.toList();
		UUID activeTeamId = details.memberships().stream()
				.filter(membership -> membership.getValidUntil() == null)
				.map(Membership::getTeamId)
				.findFirst()
				.orElse(null);
		Member member = details.member();
		return new MemberResponse(member.getId(), member.getDisplayName(), member.getFirstName(),
				member.getLastName(), member.isActive(), activeTeamId, memberships);
	}

	private MembershipResponse membershipResponse(Membership membership) {
		return new MembershipResponse(membership.getId(), membership.getTeamId(),
				teamMemberService.requireTeam(membership.getTeamId()).getName(),
				membership.getValidFrom(), membership.getValidUntil());
	}

	public record CreateMemberRequest(
			@NotBlank @Size(max = 100) String displayName,
			@Size(max = 100) String firstName,
			@Size(max = 100) String lastName,
			@NotNull UUID teamId) {
	}

	public record UpdateMemberRequest(
			@Size(min = 1, max = 100) String displayName,
			@Size(max = 100) String firstName,
			@Size(max = 100) String lastName,
			Boolean active) {
	}

	public record TeamChangeRequest(@NotNull UUID teamId, Instant effectiveAt) {
	}

	public record MembershipResponse(UUID id, UUID teamId, String teamName,
			Instant validFrom, Instant validUntil) {
	}

	public record MemberResponse(UUID id, String displayName, String firstName, String lastName,
			boolean active, UUID activeTeamId, List<MembershipResponse> memberships) {
	}

	public record MemberPage(List<MemberResponse> content, int page, int size,
			long totalElements, int totalPages) {
	}
}

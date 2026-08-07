package de.scorebound.web;

import de.scorebound.security.ScoreboundPrincipal;
import de.scorebound.teams.Team;
import de.scorebound.teams.TeamImage;
import de.scorebound.teams.TeamMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

	private final TeamMemberService teamMemberService;

	public TeamController(TeamMemberService teamMemberService) {
		this.teamMemberService = teamMemberService;
	}

	@GetMapping
	public List<TeamResponse> listTeams(@RequestParam(defaultValue = "false") boolean includeInactive,
			@AuthenticationPrincipal ScoreboundPrincipal principal) {
		if (includeInactive && !principal.roles().contains(de.scorebound.identity.Role.ADMIN)) {
			throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "forbidden",
					"Only administrators can include inactive teams");
		}
		return teamMemberService.listTeams(includeInactive).stream().map(this::response).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		Team team = teamMemberService.createTeam(request.name(), request.shortName(),
				request.color(), actor.accountId());
		return ResponseEntity.created(URI.create("/api/v1/teams/" + team.getId()))
				.body(response(team));
	}

	@GetMapping("/{teamId}")
	public TeamResponse getTeam(@PathVariable UUID teamId) {
		return response(teamMemberService.requireTeam(teamId));
	}

	@PatchMapping("/{teamId}")
	@PreAuthorize("hasRole('ADMIN')")
	public TeamResponse updateTeam(@PathVariable UUID teamId,
			@Valid @RequestBody UpdateTeamRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return response(teamMemberService.updateTeam(teamId, request.name(), request.shortName(),
				request.color(), request.active(), actor.accountId()));
	}

	@GetMapping("/{teamId}/image")
	public ResponseEntity<byte[]> getImage(@PathVariable UUID teamId) {
		TeamImage image = teamMemberService.requireTeamImage(teamId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.getContentType()))
				.header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
				.body(image.getData());
	}

	@PutMapping(value = "/{teamId}/image", consumes = { "image/png", "image/jpeg", "image/webp" })
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> replaceImage(@PathVariable UUID teamId,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		MediaType mediaType = MediaType.parseMediaType(contentType);
		String canonicalContentType = mediaType.getType() + "/" + mediaType.getSubtype();
		teamMemberService.replaceTeamImage(teamId, canonicalContentType, data, actor.accountId());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{teamId}/image")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> deleteImage(@PathVariable UUID teamId) {
		teamMemberService.deleteTeamImage(teamId);
		return ResponseEntity.noContent().build();
	}

	private TeamResponse response(Team team) {
		return new TeamResponse(team.getId(), team.getName(), team.getShortName(), team.getColor(),
				team.isActive(), teamMemberService.hasTeamImage(team.getId())
						? "/api/v1/teams/" + team.getId() + "/image" : null);
	}

	public record CreateTeamRequest(
			@NotBlank @Size(max = 100) String name,
			@NotBlank @Size(max = 20) String shortName,
			@NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color) {
	}

	public record UpdateTeamRequest(
			@Size(min = 1, max = 100) String name,
			@Size(min = 1, max = 20) String shortName,
			@Pattern(regexp = "#[0-9A-Fa-f]{6}") String color,
			Boolean active) {
	}

	public record TeamResponse(UUID id, String name, String shortName, String color,
			boolean active, String imageUrl) {
	}
}

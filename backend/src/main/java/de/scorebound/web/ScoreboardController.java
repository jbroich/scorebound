package de.scorebound.web;

import de.scorebound.competition.CompetitionPeriod;
import de.scorebound.competition.CompetitionService;
import de.scorebound.competition.PeriodParticipant;
import de.scorebound.competition.Scoreboard;
import de.scorebound.competition.ScoreboardTeam;
import de.scorebound.security.ScoreboundPrincipal;
import de.scorebound.teams.Team;
import de.scorebound.teams.TeamRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoreboards")
public class ScoreboardController {

	private final CompetitionService competitionService;
	private final TeamRepository teamRepository;

	public ScoreboardController(CompetitionService competitionService, TeamRepository teamRepository) {
		this.competitionService = competitionService;
		this.teamRepository = teamRepository;
	}

	@GetMapping
	public List<ScoreboardSummaryResponse> listScoreboards() {
		return competitionService.listActiveScoreboards().stream().map(this::summaryResponse).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ScoreboardResponse> createScoreboard(
			@Valid @RequestBody CreateScoreboardRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		Scoreboard scoreboard = competitionService.createScoreboard(request.name(),
				request.description(), actor.accountId());
		return ResponseEntity.created(URI.create("/api/v1/scoreboards/" + scoreboard.getId()))
				.body(response(competitionService.getScoreboardDetails(scoreboard.getId())));
	}

	@GetMapping("/{scoreboardId}")
	public ScoreboardResponse getScoreboard(@PathVariable UUID scoreboardId) {
		return response(competitionService.getScoreboardDetails(scoreboardId));
	}

	@PatchMapping("/{scoreboardId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ScoreboardResponse updateScoreboard(@PathVariable UUID scoreboardId,
			@Valid @RequestBody UpdateScoreboardRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		competitionService.updateScoreboard(scoreboardId, request.name(), request.description(),
				request.active(), actor.accountId());
		return response(competitionService.getScoreboardDetails(scoreboardId));
	}

	@PutMapping("/{scoreboardId}/teams/{teamId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> selectTeam(@PathVariable UUID scoreboardId,
			@PathVariable UUID teamId, @AuthenticationPrincipal ScoreboundPrincipal actor) {
		competitionService.selectTeam(scoreboardId, teamId, actor.accountId());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{scoreboardId}/teams/{teamId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> deselectTeam(@PathVariable UUID scoreboardId,
			@PathVariable UUID teamId) {
		competitionService.deselectTeam(scoreboardId, teamId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{scoreboardId}/periods")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PeriodResponse> schedulePeriod(@PathVariable UUID scoreboardId,
			@Valid @RequestBody SchedulePeriodRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		CompetitionPeriod period = competitionService.schedulePeriod(scoreboardId, request.name(),
				request.startsAt(), request.endsAt(), actor.accountId());
		return ResponseEntity.created(URI.create("/api/v1/scoreboards/" + scoreboardId
				+ "/periods/" + period.getId()))
				.body(periodResponse(competitionService.getPeriodDetails(scoreboardId, period.getId())));
	}

	@GetMapping("/{scoreboardId}/periods")
	public List<PeriodSummaryResponse> listPeriods(@PathVariable UUID scoreboardId) {
		return competitionService.listPeriods(scoreboardId).stream()
				.map(this::periodSummaryResponse).toList();
	}

	@GetMapping("/{scoreboardId}/periods/{periodId}")
	public PeriodResponse getPeriod(@PathVariable UUID scoreboardId, @PathVariable UUID periodId) {
		return periodResponse(competitionService.getPeriodDetails(scoreboardId, periodId));
	}

	@PostMapping("/{scoreboardId}/periods/{periodId}/activate")
	@PreAuthorize("hasRole('ADMIN')")
	public PeriodResponse activatePeriod(@PathVariable UUID scoreboardId, @PathVariable UUID periodId,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return periodResponse(competitionService.activatePeriod(scoreboardId, periodId,
				actor.accountId()));
	}

	@PostMapping("/{scoreboardId}/periods/{periodId}/close")
	@PreAuthorize("hasRole('ADMIN')")
	public PeriodResponse closePeriod(@PathVariable UUID scoreboardId, @PathVariable UUID periodId,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return periodResponse(competitionService.closePeriod(scoreboardId, periodId,
				actor.accountId()));
	}

	@PostMapping("/{scoreboardId}/periods/{periodId}/reopen")
	@PreAuthorize("hasRole('ADMIN')")
	public PeriodResponse reopenPeriod(@PathVariable UUID scoreboardId, @PathVariable UUID periodId,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return periodResponse(competitionService.reopenPeriod(scoreboardId, periodId,
				actor.accountId()));
	}

	@PutMapping("/{scoreboardId}/periods/{periodId}/teams/{teamId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> addTeamToActivePeriod(@PathVariable UUID scoreboardId,
			@PathVariable UUID periodId, @PathVariable UUID teamId,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		competitionService.addTeamToActivePeriod(scoreboardId, periodId, teamId, actor.accountId());
		return ResponseEntity.noContent().build();
	}

	private ScoreboardSummaryResponse summaryResponse(Scoreboard scoreboard) {
		return new ScoreboardSummaryResponse(scoreboard.getId(), scoreboard.getName(),
				scoreboard.getDescription(), scoreboard.isActive());
	}

	private ScoreboardResponse response(CompetitionService.ScoreboardDetails details) {
		List<SelectedTeamResponse> teams = details.selectedTeams().stream()
				.map(this::selectedTeamResponse).toList();
		PeriodSummaryResponse activePeriod = details.activePeriod() == null ? null
				: periodSummaryResponse(details.activePeriod());
		Scoreboard scoreboard = details.scoreboard();
		return new ScoreboardResponse(scoreboard.getId(), scoreboard.getName(),
				scoreboard.getDescription(), scoreboard.isActive(), teams, activePeriod);
	}

	private SelectedTeamResponse selectedTeamResponse(ScoreboardTeam selection) {
		Team team = requireTeam(selection.getTeamId());
		return new SelectedTeamResponse(team.getId(), team.getName(), team.getShortName(),
				team.getColor(), team.isActive(), selection.getPosition());
	}

	private PeriodSummaryResponse periodSummaryResponse(CompetitionPeriod period) {
		return new PeriodSummaryResponse(period.getId(), period.getScoreboardId(), period.getName(),
				period.getStartsAt(), period.getEndsAt(), period.getStatus().toApiValue(),
				period.getClosedAt(), period.getVisualCeiling());
	}

	private PeriodResponse periodResponse(CompetitionService.PeriodDetails details) {
		CompetitionPeriod period = details.period();
		List<ParticipantResponse> participants = details.participants().stream()
				.map(this::participantResponse).toList();
		return new PeriodResponse(period.getId(), period.getScoreboardId(), period.getName(),
				period.getStartsAt(), period.getEndsAt(), period.getStatus().toApiValue(),
				period.getClosedAt(), period.getVisualCeiling(), participants);
	}

	private ParticipantResponse participantResponse(CompetitionService.RankedParticipant ranked) {
		PeriodParticipant participant = ranked.participant();
		Team team = requireTeam(participant.getTeamId());
		return new ParticipantResponse(participant.getId(), team.getId(), team.getName(),
				team.getShortName(), team.getColor(), participant.getCurrentScore(), ranked.rank(),
				participant.isWinner());
	}

	private Team requireTeam(UUID teamId) {
		return teamRepository.findById(teamId).orElseThrow(() -> new ApiException(
				org.springframework.http.HttpStatus.NOT_FOUND, "resource_not_found",
				"Team does not exist"));
	}

	public record CreateScoreboardRequest(
			@NotBlank @Size(max = 100) String name,
			@Size(max = 500) String description) {
	}

	public record UpdateScoreboardRequest(
			@Size(min = 1, max = 100) String name,
			@Size(max = 500) String description,
			Boolean active) {
	}

	public record SchedulePeriodRequest(
			@NotBlank @Size(max = 100) String name,
			@NotNull Instant startsAt,
			@NotNull Instant endsAt) {
	}

	public record ScoreboardSummaryResponse(UUID id, String name, String description,
			boolean active) {
	}

	public record ScoreboardResponse(UUID id, String name, String description, boolean active,
			List<SelectedTeamResponse> selectedTeams, PeriodSummaryResponse activePeriod) {
	}

	public record SelectedTeamResponse(UUID id, String name, String shortName, String color,
			boolean active, int position) {
	}

	public record PeriodSummaryResponse(UUID id, UUID scoreboardId, String name, Instant startsAt,
			Instant endsAt, String status, Instant closedAt, int visualCeiling) {
	}

	public record PeriodResponse(UUID id, UUID scoreboardId, String name, Instant startsAt,
			Instant endsAt, String status, Instant closedAt, int visualCeiling,
			List<ParticipantResponse> participants) {
	}

	public record ParticipantResponse(UUID id, UUID teamId, String teamName, String shortName,
			String color, long score, int rank, boolean winner) {
	}
}

package de.scorebound.competition;

import de.scorebound.teams.Team;
import de.scorebound.teams.TeamRepository;
import de.scorebound.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CompetitionService {

	private final ScoreboardRepository scoreboardRepository;
	private final ScoreboardTeamRepository scoreboardTeamRepository;
	private final CompetitionPeriodRepository periodRepository;
	private final PeriodParticipantRepository participantRepository;
	private final TeamRepository teamRepository;

	public CompetitionService(ScoreboardRepository scoreboardRepository,
			ScoreboardTeamRepository scoreboardTeamRepository,
			CompetitionPeriodRepository periodRepository,
			PeriodParticipantRepository participantRepository, TeamRepository teamRepository) {
		this.scoreboardRepository = scoreboardRepository;
		this.scoreboardTeamRepository = scoreboardTeamRepository;
		this.periodRepository = periodRepository;
		this.participantRepository = participantRepository;
		this.teamRepository = teamRepository;
	}

	@Transactional
	public Scoreboard createScoreboard(String name, String description, UUID actorId) {
		return scoreboardRepository.save(Scoreboard.create(requiredText(name, 100, "Scoreboard name"),
				optionalText(description, 500, "Description"), actorId));
	}

	@Transactional(readOnly = true)
	public List<Scoreboard> listActiveScoreboards() {
		return scoreboardRepository.findByActiveTrueOrderByNameAsc();
	}

	@Transactional(readOnly = true)
	public Scoreboard requireScoreboard(UUID scoreboardId) {
		return scoreboardRepository.findById(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
	}

	@Transactional
	public Scoreboard updateScoreboard(UUID scoreboardId, String name, String description,
			Boolean active, UUID actorId) {
		Scoreboard scoreboard = requireScoreboard(scoreboardId);
		scoreboard.update(name == null ? null : requiredText(name, 100, "Scoreboard name"),
				description == null ? null : optionalText(description, 500, "Description"), active, actorId);
		return scoreboard;
	}

	@Transactional(readOnly = true)
	public ScoreboardDetails getScoreboardDetails(UUID scoreboardId) {
		Scoreboard scoreboard = requireScoreboard(scoreboardId);
		List<ScoreboardTeam> teams = scoreboardTeamRepository.findByScoreboardIdOrderByPositionAsc(scoreboardId);
		CompetitionPeriod activePeriod = periodRepository
				.findByScoreboardIdAndStatus(scoreboardId, PeriodStatus.ACTIVE).orElse(null);
		return new ScoreboardDetails(scoreboard, teams, activePeriod);
	}

	@Transactional
	public ScoreboardTeam selectTeam(UUID scoreboardId, UUID teamId, UUID actorId) {
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		Team team = requireTeam(teamId);
		if (!team.isActive()) {
			throw validation("Inactive teams cannot be selected for future periods");
		}
		return scoreboardTeamRepository.findById(new ScoreboardTeam.Key(scoreboardId, teamId))
				.orElseGet(() -> scoreboardTeamRepository.save(ScoreboardTeam.create(scoreboardId,
						teamId, scoreboardTeamRepository.maximumPosition(scoreboardId) + 1, actorId)));
	}

	@Transactional
	public void deselectTeam(UUID scoreboardId, UUID teamId) {
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		scoreboardTeamRepository.deleteByScoreboardIdAndTeamId(scoreboardId, teamId);
	}

	@Transactional
	public CompetitionPeriod schedulePeriod(UUID scoreboardId, String name, Instant startsAt,
			Instant endsAt, UUID actorId) {
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
			throw validation("Period end must be after its start");
		}
		if (periodRepository.hasOverlap(scoreboardId, startsAt, endsAt)) {
			throw conflict("period_overlap", "Competition periods cannot overlap");
		}
		return periodRepository.save(CompetitionPeriod.schedule(scoreboardId,
				requiredText(name, 100, "Period name"), startsAt, endsAt, actorId));
	}

	@Transactional(readOnly = true)
	public List<CompetitionPeriod> listPeriods(UUID scoreboardId) {
		requireScoreboard(scoreboardId);
		return periodRepository.findByScoreboardIdOrderByStartsAtDesc(scoreboardId);
	}

	@Transactional(readOnly = true)
	public PeriodDetails getPeriodDetails(UUID scoreboardId, UUID periodId) {
		CompetitionPeriod period = requirePeriod(scoreboardId, periodId);
		return new PeriodDetails(period, rankedParticipants(period));
	}

	@Transactional
	public PeriodDetails activatePeriod(UUID scoreboardId, UUID periodId, UUID actorId) {
		Scoreboard scoreboard = scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		CompetitionPeriod period = requirePeriodForUpdate(scoreboardId, periodId);
		activateInternal(scoreboard, period, actorId);
		return new PeriodDetails(period, rankedParticipants(period));
	}

	@Transactional
	public PeriodDetails closePeriod(UUID scoreboardId, UUID periodId, UUID actorId) {
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		CompetitionPeriod period = requirePeriodForUpdate(scoreboardId, periodId);
		closeInternal(period, Instant.now(), actorId);
		return new PeriodDetails(period, rankedParticipants(period));
	}

	@Transactional
	public PeriodDetails reopenPeriod(UUID scoreboardId, UUID periodId, UUID actorId) {
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		CompetitionPeriod period = requirePeriodForUpdate(scoreboardId, periodId);
		if (period.getStatus() != PeriodStatus.CLOSED) {
			throw conflict("closed_period", "Only a closed period can be reopened");
		}
		ensureNoActivePeriod(scoreboardId, periodId);
		period.reopen(Instant.now(), actorId);
		participantRepository.findByPeriodIdOrderByPositionAsc(periodId)
				.forEach(participant -> participant.markWinner(false));
		return new PeriodDetails(period, rankedParticipants(period));
	}

	@Transactional
	public PeriodParticipant addTeamToActivePeriod(UUID scoreboardId, UUID periodId,
			UUID teamId, UUID actorId) {
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		CompetitionPeriod period = requirePeriodForUpdate(scoreboardId, periodId);
		if (period.getStatus() != PeriodStatus.ACTIVE) {
			throw conflict("period_not_active", "Teams can only join an active period");
		}
		if (!scoreboardTeamRepository.existsByScoreboardIdAndTeamId(scoreboardId, teamId)) {
			throw validation("Team must be selected on the scoreboard first");
		}
		requireActiveTeam(teamId);
		return participantRepository.findByPeriodIdAndTeamId(periodId, teamId)
				.orElseGet(() -> participantRepository.save(PeriodParticipant.create(periodId, teamId,
						participantRepository.maximumPosition(periodId) + 1, actorId)));
	}

	@Transactional
	public void processLifecycle(Instant now) {
		List<CompetitionPeriod> dueClosures = periodRepository
				.findByStatusAndEndsAtLessThanEqualAndReopenedAtIsNullOrderByEndsAtAsc(
						PeriodStatus.ACTIVE, now);
		for (CompetitionPeriod candidate : dueClosures) {
			scoreboardRepository.findByIdForUpdate(candidate.getScoreboardId()).ifPresent(scoreboard -> {
				CompetitionPeriod period = periodRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
				if (period.getStatus() == PeriodStatus.ACTIVE && period.getReopenedAt() == null) {
					closeInternal(period, now, null);
				}
			});
		}

		List<CompetitionPeriod> dueStarts = periodRepository
				.findByStatusAndStartsAtLessThanEqualOrderByStartsAtAsc(PeriodStatus.SCHEDULED, now);
		for (CompetitionPeriod candidate : dueStarts) {
			Scoreboard scoreboard = scoreboardRepository.findByIdForUpdate(candidate.getScoreboardId())
					.orElse(null);
			if (scoreboard == null || periodRepository
					.findByScoreboardIdAndStatus(scoreboard.getId(), PeriodStatus.ACTIVE).isPresent()) {
				continue;
			}
			CompetitionPeriod period = periodRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
			if (period.getStatus() == PeriodStatus.SCHEDULED) {
				activateInternal(scoreboard, period, null);
				if (!period.getEndsAt().isAfter(now)) {
					closeInternal(period, now, null);
				}
			}
		}
	}

	private void activateInternal(Scoreboard scoreboard, CompetitionPeriod period, UUID actorId) {
		if (!scoreboard.isActive()) {
			throw validation("Inactive scoreboards cannot activate periods");
		}
		if (period.getStatus() != PeriodStatus.SCHEDULED) {
			throw conflict("period_not_scheduled", "Only scheduled periods can be activated");
		}
		ensureNoActivePeriod(scoreboard.getId(), period.getId());
		List<ScoreboardTeam> selections = scoreboardTeamRepository
				.findByScoreboardIdOrderByPositionAsc(scoreboard.getId());
		List<ScoreboardTeam> eligible = selections.stream()
				.filter(selection -> teamRepository.findById(selection.getTeamId())
						.map(Team::isActive).orElse(false))
				.toList();
		if (eligible.isEmpty()) {
			throw validation("Select at least one active team before activating a period");
		}
		period.activate();
		for (int index = 0; index < eligible.size(); index++) {
			participantRepository.save(PeriodParticipant.create(period.getId(),
					eligible.get(index).getTeamId(), index, actorId));
		}
	}

	private void closeInternal(CompetitionPeriod period, Instant closedAt, UUID actorId) {
		if (period.getStatus() != PeriodStatus.ACTIVE) {
			throw conflict("period_not_active", "Only active periods can be closed");
		}
		List<PeriodParticipant> participants = participantRepository
				.findByPeriodIdOrderByPositionAsc(period.getId());
		int winningScore = participants.stream().mapToInt(PeriodParticipant::getCurrentScore)
				.max().orElse(0);
		participants.forEach(participant -> participant
				.markWinner(participant.getCurrentScore() == winningScore));
		period.close(closedAt, actorId);
	}

	private List<RankedParticipant> rankedParticipants(CompetitionPeriod period) {
		List<PeriodParticipant> participants = new ArrayList<>(participantRepository
				.findByPeriodIdOrderByPositionAsc(period.getId()));
		participants.sort(Comparator.comparingInt(PeriodParticipant::getCurrentScore).reversed()
				.thenComparingInt(PeriodParticipant::getPosition));
		List<RankedParticipant> ranked = new ArrayList<>();
		Integer previousScore = null;
		int rank = 0;
		for (int index = 0; index < participants.size(); index++) {
			PeriodParticipant participant = participants.get(index);
			if (previousScore == null || participant.getCurrentScore() != previousScore) {
				rank = index + 1;
			}
			ranked.add(new RankedParticipant(participant, rank));
			previousScore = participant.getCurrentScore();
		}
		return List.copyOf(ranked);
	}

	private void ensureNoActivePeriod(UUID scoreboardId, UUID ignoredPeriodId) {
		periodRepository.findByScoreboardIdAndStatus(scoreboardId, PeriodStatus.ACTIVE)
				.filter(active -> !active.getId().equals(ignoredPeriodId))
				.ifPresent(active -> {
					throw conflict("period_overlap", "Scoreboard already has an active period");
				});
	}

	private CompetitionPeriod requirePeriod(UUID scoreboardId, UUID periodId) {
		return periodRepository.findByIdAndScoreboardId(periodId, scoreboardId)
				.orElseThrow(() -> notFound("Competition period does not exist"));
	}

	private CompetitionPeriod requirePeriodForUpdate(UUID scoreboardId, UUID periodId) {
		CompetitionPeriod period = periodRepository.findByIdForUpdate(periodId)
				.orElseThrow(() -> notFound("Competition period does not exist"));
		if (!period.getScoreboardId().equals(scoreboardId)) {
			throw notFound("Competition period does not exist");
		}
		return period;
	}

	private Team requireTeam(UUID teamId) {
		return teamRepository.findById(teamId).orElseThrow(() -> notFound("Team does not exist"));
	}

	private Team requireActiveTeam(UUID teamId) {
		Team team = requireTeam(teamId);
		if (!team.isActive()) {
			throw validation("Inactive teams cannot join a period");
		}
		return team;
	}

	private static String requiredText(String value, int maximumLength, String field) {
		if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
			throw validation(field + " must contain 1 to " + maximumLength + " characters");
		}
		return value.trim();
	}

	private static String optionalText(String value, int maximumLength, String field) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (value.trim().length() > maximumLength) {
			throw validation(field + " must contain at most " + maximumLength + " characters");
		}
		return value.trim();
	}

	private static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
	}

	private static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "resource_not_found", message);
	}

	public record ScoreboardDetails(Scoreboard scoreboard, List<ScoreboardTeam> selectedTeams,
			CompetitionPeriod activePeriod) {
	}

	public record PeriodDetails(CompetitionPeriod period, List<RankedParticipant> participants) {
	}

	public record RankedParticipant(PeriodParticipant participant, int rank) {
	}
}

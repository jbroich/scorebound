package de.scorebound.web;

import de.scorebound.competition.CompetitionPeriod;
import de.scorebound.competition.CompetitionService;
import de.scorebound.competition.PeriodParticipant;
import de.scorebound.display.DisplayConfigurationService;
import de.scorebound.identity.AccountRepository;
import de.scorebound.scoring.ScoreCancellation;
import de.scorebound.scoring.ScoreKind;
import de.scorebound.scoring.ScoreTransaction;
import de.scorebound.scoring.ScoringService;
import de.scorebound.security.ScoreboundPrincipal;
import de.scorebound.teams.MemberRepository;
import de.scorebound.teams.Team;
import de.scorebound.teams.TeamRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoreboards/{scoreboardId}")
public class ScoringController {

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final ScoringService scoringService;
	private final CompetitionService competitionService;
	private final TeamRepository teamRepository;
	private final MemberRepository memberRepository;
	private final AccountRepository accountRepository;
	private final DisplayConfigurationService displayService;

	public ScoringController(ScoringService scoringService, CompetitionService competitionService,
			TeamRepository teamRepository, MemberRepository memberRepository,
			AccountRepository accountRepository, DisplayConfigurationService displayService) {
		this.scoringService = scoringService;
		this.competitionService = competitionService;
		this.teamRepository = teamRepository;
		this.memberRepository = memberRepository;
		this.accountRepository = accountRepository;
		this.displayService = displayService;
	}

	@GetMapping("/standings")
	public StandingsResponse getStandings(@PathVariable UUID scoreboardId,
			@RequestParam(required = false) UUID periodId, Authentication authentication,
			@AuthenticationPrincipal ScoreboundPrincipal principal) {
		requireDisplayAssignment(scoreboardId, authentication, principal);
		CompetitionPeriod period = scoringService.resolvePeriod(scoreboardId, periodId);
		return standingsResponse(competitionService.getPeriodDetails(scoreboardId, period.getId()));
	}

	@GetMapping("/transactions")
	public TransactionPageResponse listTransactions(@PathVariable UUID scoreboardId,
			@RequestParam(required = false) UUID periodId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size, Authentication authentication,
			@AuthenticationPrincipal ScoreboundPrincipal principal) {
		requireDisplayAssignment(scoreboardId, authentication, principal);
		ScoringService.TransactionPage result = scoringService.listTransactions(scoreboardId,
				periodId, page, size);
		return new TransactionPageResponse(result.period().getId(),
				result.content().stream().map(this::transactionResponse).toList(), result.page(),
				result.size(), result.totalElements(), result.totalPages());
	}

	@PostMapping("/transactions")
	@PreAuthorize("hasAnyRole('ADMIN', 'SCORER')")
	public TransactionResponse record(@PathVariable UUID scoreboardId,
			@Valid @RequestBody ScoreRequest request,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return transactionResponse(scoringService.record(scoreboardId, request.teamId(),
				request.memberId(), request.kind(), request.amount(), request.reason(), idempotencyKey,
				actor.accountId(), actor.roles()));
	}

	@PostMapping("/transactions/{transactionId}/cancellation")
	@PreAuthorize("hasAnyRole('ADMIN', 'SCORER')")
	public TransactionResponse cancel(@PathVariable UUID scoreboardId,
			@PathVariable UUID transactionId, @Valid @RequestBody CancellationRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal actor) {
		return transactionResponse(scoringService.cancel(scoreboardId, transactionId,
				request.reason(), actor.accountId(), actor.roles()));
	}

	private StandingsResponse standingsResponse(CompetitionService.PeriodDetails details) {
		CompetitionPeriod period = details.period();
		List<StandingResponse> standings = details.participants().stream().map(ranked -> {
			PeriodParticipant participant = ranked.participant();
			Team team = requireTeam(participant.getTeamId());
			return new StandingResponse(team.getId(), team.getName(), team.getShortName(),
					team.getColor(), participant.getCurrentScore(), ranked.rank(),
					participant.isWinner());
		}).toList();
		return new StandingsResponse(period.getId(), period.getName(), period.getStatus().toApiValue(),
				period.getStartsAt(), period.getEndsAt(), period.getVisualCeiling(), standings);
	}

	private TransactionResponse transactionResponse(ScoringService.TransactionDetails details) {
		ScoreTransaction transaction = details.transaction();
		ScoreCancellation cancellation = details.cancellation();
		Team team = requireTeam(transaction.getTeamId());
		String memberName = transaction.getMemberId() == null ? null : memberRepository
				.findById(transaction.getMemberId()).map(member -> member.getDisplayName()).orElse(null);
		String actorName = accountRepository.findById(transaction.getCreatedBy())
				.map(account -> account.getUsername()).orElse(null);
		CancellationResponse cancellationResponse = cancellation == null ? null
				: new CancellationResponse(cancellation.getReason(), cancellation.getCreatedAt(),
						cancellation.getCreatedBy(), accountRepository.findById(cancellation.getCreatedBy())
								.map(account -> account.getUsername()).orElse(null));
		return new TransactionResponse(transaction.getId(), transaction.getPeriodId(), team.getId(),
				team.getName(), transaction.getMemberId(), memberName,
				transaction.getKind().toApiValue(), transaction.getAmount(), transaction.getReason(),
				transaction.getCreatedAt(), transaction.getCreatedBy(), actorName,
				cancellationResponse, details.resultingTeamScore());
	}

	private Team requireTeam(UUID teamId) {
		return teamRepository.findById(teamId).orElseThrow(() -> new ApiException(
				org.springframework.http.HttpStatus.NOT_FOUND, "resource_not_found",
				"Team does not exist"));
	}

	private void requireDisplayAssignment(UUID scoreboardId, Authentication authentication,
			ScoreboundPrincipal principal) {
		boolean displayOnly = authentication.getAuthorities().stream()
				.filter(authority -> authority.getAuthority().startsWith("ROLE_"))
				.allMatch(authority -> authority.getAuthority().equals("ROLE_DISPLAY"));
		if (displayOnly) {
			displayService.requireAssigned(principal.accountId(), scoreboardId);
		}
	}

	public record ScoreRequest(UUID teamId, UUID memberId, @NotNull ScoreKind kind,
			@Min(1) @Max(1000) int amount, @NotBlank @Size(max = 500) String reason) {
	}

	public record CancellationRequest(@NotBlank @Size(max = 500) String reason) {
	}

	public record StandingsResponse(UUID periodId, String periodName, String status,
			Instant startsAt, Instant endsAt, int visualCeiling, List<StandingResponse> standings) {
	}

	public record StandingResponse(UUID teamId, String teamName, String shortName, String color,
			long score, int rank, boolean winner) {
	}

	public record TransactionResponse(UUID id, UUID periodId, UUID teamId, String teamName,
			UUID memberId, String memberName, String kind, int amount, String reason,
			Instant createdAt, UUID createdBy, String createdByUsername,
			CancellationResponse cancellation, Long resultingTeamScore) {
	}

	public record CancellationResponse(String reason, Instant createdAt, UUID createdBy,
			String createdByUsername) {
	}

	public record TransactionPageResponse(UUID periodId, List<TransactionResponse> content,
			int page, int size, long totalElements, int totalPages) {
	}
}

package de.scorebound.scoring;

import de.scorebound.competition.CompetitionPeriod;
import de.scorebound.competition.CompetitionPeriodRepository;
import de.scorebound.competition.PeriodParticipant;
import de.scorebound.competition.PeriodParticipantRepository;
import de.scorebound.competition.PeriodStatus;
import de.scorebound.competition.ScoreboardRepository;
import de.scorebound.identity.Account;
import de.scorebound.identity.AccountRepository;
import de.scorebound.identity.Role;
import de.scorebound.teams.Member;
import de.scorebound.teams.MemberRepository;
import de.scorebound.teams.Membership;
import de.scorebound.teams.MembershipRepository;
import de.scorebound.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScoringService {

	private final ScoreboardRepository scoreboardRepository;
	private final CompetitionPeriodRepository periodRepository;
	private final PeriodParticipantRepository participantRepository;
	private final MemberRepository memberRepository;
	private final MembershipRepository membershipRepository;
	private final AccountRepository accountRepository;
	private final ScorerAssignmentRepository assignmentRepository;
	private final ScoreTransactionRepository transactionRepository;
	private final ScoreCancellationRepository cancellationRepository;

	public ScoringService(ScoreboardRepository scoreboardRepository,
			CompetitionPeriodRepository periodRepository,
			PeriodParticipantRepository participantRepository, MemberRepository memberRepository,
			MembershipRepository membershipRepository, AccountRepository accountRepository,
			ScorerAssignmentRepository assignmentRepository,
			ScoreTransactionRepository transactionRepository,
			ScoreCancellationRepository cancellationRepository) {
		this.scoreboardRepository = scoreboardRepository;
		this.periodRepository = periodRepository;
		this.participantRepository = participantRepository;
		this.memberRepository = memberRepository;
		this.membershipRepository = membershipRepository;
		this.accountRepository = accountRepository;
		this.assignmentRepository = assignmentRepository;
		this.transactionRepository = transactionRepository;
		this.cancellationRepository = cancellationRepository;
	}

	@Transactional
	public ScorerAssignment assignScorer(UUID accountId, UUID scoreboardId, UUID actorId) {
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> notFound("Account does not exist"));
		if (!account.getRoles().contains(Role.SCORER)) {
			throw validation("Account must have the Scorer role");
		}
		if (!scoreboardRepository.existsById(scoreboardId)) {
			throw notFound("Scoreboard does not exist");
		}
		ScorerAssignment.Key key = new ScorerAssignment.Key(accountId, scoreboardId);
		return assignmentRepository.findById(key).orElseGet(() -> assignmentRepository
				.save(ScorerAssignment.create(accountId, scoreboardId, actorId)));
	}

	@Transactional
	public void removeScorerAssignment(UUID accountId, UUID scoreboardId) {
		assignmentRepository.deleteByAccountIdAndScoreboardId(accountId, scoreboardId);
	}

	@Transactional(readOnly = true)
	public List<UUID> listScorerAssignments(UUID accountId) {
		if (!accountRepository.existsById(accountId)) {
			throw notFound("Account does not exist");
		}
		return assignmentRepository.findByAccountIdOrderByScoreboardId(accountId).stream()
				.map(ScorerAssignment::getScoreboardId).toList();
	}

	@Transactional
	public TransactionDetails record(UUID scoreboardId, UUID teamId, UUID memberId,
			ScoreKind kind, int amount, String reason, String idempotencyKey,
			UUID actorId, Set<Role> roles) {
		requireScoringAccess(scoreboardId, actorId, roles);
		if ((teamId == null) == (memberId == null)) {
			throw validation("Provide exactly one of teamId or memberId");
		}
		if (kind == null || amount < 1 || amount > 1000) {
			throw validation("Amount must be an integer from 1 through 1000");
		}
		String normalizedReason = requiredText(reason, 500, "Reason");
		String normalizedKey = optionalText(idempotencyKey, 128, "Idempotency-Key");
		if (normalizedKey != null) {
			ScoreTransaction existing = transactionRepository
					.findByCreatedByAndIdempotencyKey(actorId, normalizedKey).orElse(null);
			if (existing != null) {
				CompetitionPeriod existingPeriod = periodRepository.findById(existing.getPeriodId())
						.orElseThrow(() -> notFound("Competition period does not exist"));
				if (!existingPeriod.getScoreboardId().equals(scoreboardId)
						|| !existing.matches(teamId, memberId, kind, amount,
						normalizedReason)) {
					throw conflict("idempotency_key_reused",
							"Idempotency-Key was already used for different transaction content");
				}
				return details(existing);
			}
		}

		CompetitionPeriod period = requireActivePeriodForUpdate(scoreboardId);
		UUID resolvedTeamId = teamId == null ? resolveMemberTeam(memberId) : teamId;
		PeriodParticipant participant = participantRepository
				.findByPeriodIdAndTeamIdForUpdate(period.getId(), resolvedTeamId)
				.orElseThrow(() -> validation("Target team does not participate in the active period"));
		long adjustment = kind.apply(amount);
		long updatedScore;
		try {
			updatedScore = Math.addExact(participant.getCurrentScore(), adjustment);
		} catch (ArithmeticException exception) {
			throw conflict("score_limit_exceeded", "Score exceeds the supported range");
		}
		if (updatedScore < 0) {
			throw conflict("score_would_be_negative", "Debit exceeds the team's current score");
		}

		ScoreTransaction transaction = transactionRepository.save(ScoreTransaction.create(
				period.getId(), resolvedTeamId, memberId, kind, amount, updatedScore, normalizedReason,
				actorId, normalizedKey));
		participant.adjustScore(adjustment);
		return new TransactionDetails(transaction, null, updatedScore);
	}

	@Transactional
	public TransactionDetails cancel(UUID scoreboardId, UUID transactionId, String reason,
			UUID actorId, Set<Role> roles) {
		ScoreTransaction transaction = transactionRepository.findById(transactionId)
				.orElseThrow(() -> notFound("Score transaction does not exist"));
		CompetitionPeriod period = periodRepository.findById(transaction.getPeriodId())
				.orElseThrow(() -> notFound("Competition period does not exist"));
		if (!period.getScoreboardId().equals(scoreboardId)) {
			throw notFound("Score transaction does not exist");
		}
		requireScoringAccess(scoreboardId, actorId, roles);
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		period = periodRepository.findByIdForUpdate(period.getId())
				.orElseThrow(() -> notFound("Competition period does not exist"));
		if (!roles.contains(Role.ADMIN) && !transaction.getCreatedBy().equals(actorId)) {
			throw forbidden("Scorers can only cancel their own transactions");
		}
		if (period.getStatus() != PeriodStatus.ACTIVE) {
			throw conflict("period_not_active", "Closed periods reject score changes until reopened");
		}
		if (cancellationRepository.existsById(transactionId)) {
			throw conflict("transaction_already_cancelled", "Transaction is already cancelled");
		}
		String normalizedReason = requiredText(reason, 500, "Cancellation reason");
		PeriodParticipant participant = participantRepository
				.findByPeriodIdAndTeamIdForUpdate(period.getId(), transaction.getTeamId())
				.orElseThrow(() -> conflict("participant_missing",
						"Historical period participant is missing"));
		long reversal = -transaction.getKind().apply(transaction.getAmount());
		long updatedScore;
		try {
			updatedScore = Math.addExact(participant.getCurrentScore(), reversal);
		} catch (ArithmeticException exception) {
			throw conflict("score_limit_exceeded", "Score exceeds the supported range");
		}
		if (updatedScore < 0) {
			throw conflict("score_would_be_negative",
					"Cancelling this credit would make the team score negative");
		}
		ScoreCancellation cancellation = cancellationRepository.save(
				ScoreCancellation.create(transactionId, normalizedReason, actorId));
		participant.adjustScore(reversal);
		return new TransactionDetails(transaction, cancellation, updatedScore);
	}

	@Transactional(readOnly = true)
	public TransactionPage listTransactions(UUID scoreboardId, UUID requestedPeriodId,
			int page, int size) {
		CompetitionPeriod period = resolvePeriod(scoreboardId, requestedPeriodId);
		if (page < 0 || size < 1 || size > 100) {
			throw validation("Page must be non-negative and size must be from 1 through 100");
		}
		Page<ScoreTransaction> transactions = transactionRepository.findByPeriodId(period.getId(),
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
		Map<UUID, ScoreCancellation> cancellations = cancellationRepository
				.findByTransactionIdIn(transactions.getContent().stream()
						.map(ScoreTransaction::getId).toList()).stream()
				.collect(Collectors.toMap(ScoreCancellation::getTransactionId, Function.identity()));
		List<TransactionDetails> content = transactions.getContent().stream()
				.map(transaction -> new TransactionDetails(transaction,
						cancellations.get(transaction.getId()), null)).toList();
		return new TransactionPage(period, content, transactions.getNumber(), transactions.getSize(),
				transactions.getTotalElements(), transactions.getTotalPages());
	}

	@Transactional(readOnly = true)
	public CompetitionPeriod resolvePeriod(UUID scoreboardId, UUID requestedPeriodId) {
		if (!scoreboardRepository.existsById(scoreboardId)) {
			throw notFound("Scoreboard does not exist");
		}
		if (requestedPeriodId != null) {
			return periodRepository.findByIdAndScoreboardId(requestedPeriodId, scoreboardId)
					.orElseThrow(() -> notFound("Competition period does not exist"));
		}
		return periodRepository.findByScoreboardIdAndStatus(scoreboardId, PeriodStatus.ACTIVE)
				.orElseGet(() -> periodRepository.findByScoreboardIdOrderByStartsAtDesc(scoreboardId)
						.stream().findFirst()
						.orElseThrow(() -> notFound("Scoreboard has no competition periods")));
	}

	private TransactionDetails details(ScoreTransaction transaction) {
		ScoreCancellation cancellation = cancellationRepository.findById(transaction.getId())
				.orElse(null);
		return new TransactionDetails(transaction, cancellation, transaction.getResultingScore());
	}

	private CompetitionPeriod requireActivePeriodForUpdate(UUID scoreboardId) {
		scoreboardRepository.findByIdForUpdate(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		CompetitionPeriod period = periodRepository
				.findByScoreboardIdAndStatus(scoreboardId, PeriodStatus.ACTIVE)
				.orElseThrow(() -> conflict("period_not_active",
						"Scoreboard does not have an active period"));
		return periodRepository.findByIdForUpdate(period.getId())
				.orElseThrow(() -> notFound("Competition period does not exist"));
	}

	private UUID resolveMemberTeam(UUID memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> notFound("Member does not exist"));
		if (!member.isActive()) {
			throw validation("Inactive members cannot receive score transactions");
		}
		return membershipRepository.findByMemberIdAndValidUntilIsNull(memberId)
				.map(Membership::getTeamId)
				.orElseThrow(() -> validation("Member does not have an active team"));
	}

	private void requireScoringAccess(UUID scoreboardId, UUID actorId, Set<Role> roles) {
		if (roles.contains(Role.ADMIN)) {
			return;
		}
		if (!roles.contains(Role.SCORER)
				|| !assignmentRepository.existsByAccountIdAndScoreboardId(actorId, scoreboardId)) {
			throw forbidden("Scorer is not assigned to this scoreboard");
		}
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

	private static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message);
	}

	private static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "resource_not_found", message);
	}

	public record TransactionDetails(ScoreTransaction transaction,
			ScoreCancellation cancellation, Long resultingTeamScore) {
	}

	public record TransactionPage(CompetitionPeriod period, List<TransactionDetails> content,
			int page, int size, long totalElements, int totalPages) {
	}
}

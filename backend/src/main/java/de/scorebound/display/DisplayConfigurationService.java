package de.scorebound.display;

import de.scorebound.competition.Scoreboard;
import de.scorebound.competition.ScoreboardRepository;
import de.scorebound.identity.Account;
import de.scorebound.identity.AccountRepository;
import de.scorebound.identity.Role;
import de.scorebound.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DisplayConfigurationService {
	private final DisplayAssignmentRepository assignmentRepository;
	private final DisplayConfigurationRepository configurationRepository;
	private final AccountRepository accountRepository;
	private final ScoreboardRepository scoreboardRepository;

	public DisplayConfigurationService(DisplayAssignmentRepository assignmentRepository,
			DisplayConfigurationRepository configurationRepository,
			AccountRepository accountRepository, ScoreboardRepository scoreboardRepository) {
		this.assignmentRepository = assignmentRepository;
		this.configurationRepository = configurationRepository;
		this.accountRepository = accountRepository;
		this.scoreboardRepository = scoreboardRepository;
	}

	@Transactional
	public DisplayAssignment assign(UUID accountId, UUID scoreboardId, UUID actorId) {
		Account account = requireDisplayAccount(accountId);
		Scoreboard scoreboard = scoreboardRepository.findById(scoreboardId)
				.orElseThrow(() -> notFound("Scoreboard does not exist"));
		if (!scoreboard.isActive()) throw validation("Inactive scoreboards cannot be assigned");
		DisplayAssignment.Key key = new DisplayAssignment.Key(account.getId(), scoreboardId);
		return assignmentRepository.findById(key).orElseGet(() -> assignmentRepository.save(
				DisplayAssignment.create(accountId, scoreboardId,
						assignmentRepository.maximumPosition(accountId) + 1, actorId)));
	}

	@Transactional
	public void remove(UUID accountId, UUID scoreboardId) {
		assignmentRepository.deleteByAccountIdAndScoreboardId(accountId, scoreboardId);
		configurationRepository.findById(accountId).ifPresent(configuration -> {
			if (scoreboardId.equals(configuration.getFixedScoreboardId())) {
				UUID replacement = assignmentRepository.findByAccountIdOrderByPositionAsc(accountId)
						.stream().map(DisplayAssignment::getScoreboardId).findFirst().orElse(null);
				configuration.update(configuration.getMode(), replacement,
						configuration.getRotationSeconds(), configuration.isSoundEnabled());
			}
		});
	}

	@Transactional(readOnly = true)
	public List<UUID> listAssignmentIds(UUID accountId) {
		return assignmentRepository.findByAccountIdOrderByPositionAsc(accountId).stream()
				.map(DisplayAssignment::getScoreboardId).toList();
	}

	@Transactional
	public Settings getSettings(UUID accountId) {
		requireDisplayAccount(accountId);
		List<Scoreboard> scoreboards = assignedScoreboards(accountId);
		DisplayConfiguration configuration = configurationRepository.findById(accountId)
				.orElseGet(() -> configurationRepository.save(DisplayConfiguration.defaults(accountId,
						scoreboards.isEmpty() ? null : scoreboards.getFirst().getId())));
		return new Settings(configuration, scoreboards);
	}

	@Transactional
	public Settings updateSettings(UUID accountId, DisplayMode mode, UUID fixedScoreboardId,
			int rotationSeconds, boolean soundEnabled) {
		List<Scoreboard> scoreboards = assignedScoreboards(accountId);
		if (mode == null) throw validation("Display mode is required");
		if (rotationSeconds < 10 || rotationSeconds > 300) {
			throw validation("Rotation interval must be from 10 through 300 seconds");
		}
		if (mode == DisplayMode.FIXED && (fixedScoreboardId == null
				|| scoreboards.stream().noneMatch(board -> board.getId().equals(fixedScoreboardId)))) {
			throw validation("Fixed scoreboard must be assigned to this display");
		}
		DisplayConfiguration configuration = configurationRepository.findById(accountId)
				.orElseGet(() -> DisplayConfiguration.defaults(accountId, fixedScoreboardId));
		configuration.update(mode, fixedScoreboardId, rotationSeconds, soundEnabled);
		configurationRepository.save(configuration);
		return new Settings(configuration, scoreboards);
	}

	@Transactional(readOnly = true)
	public void requireAssigned(UUID accountId, UUID scoreboardId) {
		if (!assignmentRepository.existsByAccountIdAndScoreboardId(accountId, scoreboardId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "forbidden",
					"Display is not assigned to this scoreboard");
		}
	}

	private List<Scoreboard> assignedScoreboards(UUID accountId) {
		requireDisplayAccount(accountId);
		return listAssignmentIds(accountId).stream().map(id -> scoreboardRepository.findById(id)
				.orElseThrow(() -> notFound("Assigned scoreboard does not exist"))).toList();
	}

	private Account requireDisplayAccount(UUID accountId) {
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> notFound("Account does not exist"));
		if (!account.getRoles().contains(Role.DISPLAY)) {
			throw validation("Account must have the Display role");
		}
		return account;
	}

	private static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
	}

	private static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "resource_not_found", message);
	}

	public record Settings(DisplayConfiguration configuration, List<Scoreboard> scoreboards) {
	}
}

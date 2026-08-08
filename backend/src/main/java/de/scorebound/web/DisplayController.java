package de.scorebound.web;

import de.scorebound.competition.Scoreboard;
import de.scorebound.display.DisplayConfiguration;
import de.scorebound.display.DisplayConfigurationService;
import de.scorebound.display.DisplayMode;
import de.scorebound.security.ScoreboundPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/display/configuration")
@PreAuthorize("hasRole('DISPLAY')")
public class DisplayController {
	private final DisplayConfigurationService displayService;

	public DisplayController(DisplayConfigurationService displayService) {
		this.displayService = displayService;
	}

	@GetMapping
	public DisplayConfigurationResponse getConfiguration(
			@AuthenticationPrincipal ScoreboundPrincipal principal) {
		return response(displayService.getSettings(principal.accountId()));
	}

	@PutMapping
	public DisplayConfigurationResponse updateConfiguration(
			@Valid @RequestBody UpdateDisplayConfigurationRequest request,
			@AuthenticationPrincipal ScoreboundPrincipal principal) {
		return response(displayService.updateSettings(principal.accountId(), request.mode(),
				request.fixedScoreboardId(), request.rotationSeconds(), request.soundEnabled()));
	}

	private static DisplayConfigurationResponse response(DisplayConfigurationService.Settings settings) {
		DisplayConfiguration configuration = settings.configuration();
		return new DisplayConfigurationResponse(configuration.getMode(),
				configuration.getFixedScoreboardId(), configuration.getRotationSeconds(),
				configuration.isSoundEnabled(), settings.scoreboards().stream()
						.map(DisplayController::scoreboardResponse).toList());
	}

	private static AssignedScoreboardResponse scoreboardResponse(Scoreboard scoreboard) {
		return new AssignedScoreboardResponse(scoreboard.getId(), scoreboard.getName(),
				scoreboard.getDescription());
	}

	public record UpdateDisplayConfigurationRequest(@NotNull DisplayMode mode,
			UUID fixedScoreboardId, @Min(10) @Max(300) int rotationSeconds,
			boolean soundEnabled) {
	}

	public record DisplayConfigurationResponse(DisplayMode mode, UUID fixedScoreboardId,
			int rotationSeconds, boolean soundEnabled,
			List<AssignedScoreboardResponse> scoreboards) {
	}

	public record AssignedScoreboardResponse(UUID id, String name, String description) {
	}
}

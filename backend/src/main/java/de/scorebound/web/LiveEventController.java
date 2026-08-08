package de.scorebound.web;

import de.scorebound.competition.CompetitionService;
import de.scorebound.display.DisplayConfigurationService;
import de.scorebound.live.ScoreboardEventStream;
import de.scorebound.security.ScoreboundPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoreboards/{scoreboardId}/events")
public class LiveEventController {

	private final CompetitionService competitionService;
	private final ScoreboardEventStream eventStream;
	private final DisplayConfigurationService displayService;

	public LiveEventController(CompetitionService competitionService,
			ScoreboardEventStream eventStream, DisplayConfigurationService displayService) {
		this.competitionService = competitionService;
		this.eventStream = eventStream;
		this.displayService = displayService;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(@PathVariable UUID scoreboardId, Authentication authentication,
			@AuthenticationPrincipal ScoreboundPrincipal principal) {
		competitionService.requireScoreboard(scoreboardId);
		boolean displayOnly = authentication.getAuthorities().stream()
				.filter(authority -> authority.getAuthority().startsWith("ROLE_"))
				.allMatch(authority -> authority.getAuthority().equals("ROLE_DISPLAY"));
		if (displayOnly) {
			displayService.requireAssigned(principal.accountId(), scoreboardId);
		}
		return eventStream.subscribe(scoreboardId);
	}
}

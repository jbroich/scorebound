package de.scorebound.web;

import de.scorebound.competition.CompetitionService;
import de.scorebound.live.ScoreboardEventStream;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoreboards/{scoreboardId}/events")
public class LiveEventController {

	private final CompetitionService competitionService;
	private final ScoreboardEventStream eventStream;

	public LiveEventController(CompetitionService competitionService,
			ScoreboardEventStream eventStream) {
		this.competitionService = competitionService;
		this.eventStream = eventStream;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(@PathVariable UUID scoreboardId) {
		competitionService.requireScoreboard(scoreboardId);
		return eventStream.subscribe(scoreboardId);
	}
}

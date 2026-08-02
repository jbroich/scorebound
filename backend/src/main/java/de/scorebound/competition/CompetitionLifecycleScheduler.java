package de.scorebound.competition;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CompetitionLifecycleScheduler {

	private final CompetitionService competitionService;

	public CompetitionLifecycleScheduler(CompetitionService competitionService) {
		this.competitionService = competitionService;
	}

	@Scheduled(fixedDelayString = "${scorebound.competition.lifecycle-delay:30000}")
	public void processLifecycle() {
		competitionService.processLifecycle(Instant.now());
	}
}

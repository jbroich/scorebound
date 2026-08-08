package de.scorebound.live;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Component
public class ScoreboardEventPublisher {

	private final ScoreboardEventStream eventStream;

	public ScoreboardEventPublisher(ScoreboardEventStream eventStream) {
		this.eventStream = eventStream;
	}

	public void publishAfterCommit(UUID scoreboardId, ScoreboardEventType type) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			eventStream.publish(scoreboardId, type);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				eventStream.publish(scoreboardId, type);
			}
		});
	}
}

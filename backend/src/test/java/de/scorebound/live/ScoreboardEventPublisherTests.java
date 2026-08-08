package de.scorebound.live;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ScoreboardEventPublisherTests {

	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void publishesImmediatelyWithoutTransaction() {
		ScoreboardEventStream stream = mock(ScoreboardEventStream.class);
		ScoreboardEventPublisher publisher = new ScoreboardEventPublisher(stream);
		UUID scoreboardId = UUID.randomUUID();

		publisher.publishAfterCommit(scoreboardId, ScoreboardEventType.SCORE_CREATED);

		verify(stream).publish(scoreboardId, ScoreboardEventType.SCORE_CREATED);
	}

	@Test
	void waitsUntilTheTransactionCommits() {
		ScoreboardEventStream stream = mock(ScoreboardEventStream.class);
		ScoreboardEventPublisher publisher = new ScoreboardEventPublisher(stream);
		UUID scoreboardId = UUID.randomUUID();
		TransactionSynchronizationManager.initSynchronization();

		publisher.publishAfterCommit(scoreboardId, ScoreboardEventType.SCORE_CANCELLED);
		verify(stream, never()).publish(scoreboardId, ScoreboardEventType.SCORE_CANCELLED);

		TransactionSynchronizationManager.getSynchronizations().forEach(
				TransactionSynchronization::afterCommit);
		verify(stream).publish(scoreboardId, ScoreboardEventType.SCORE_CANCELLED);
	}
}

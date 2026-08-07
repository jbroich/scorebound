package de.scorebound.live;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ScoreboardEventStream {

	private final AtomicLong sequence = new AtomicLong();
	private final Map<UUID, BoardSubscribers> boards = new ConcurrentHashMap<>();

	public SseEmitter subscribe(UUID scoreboardId) {
		SseEmitter emitter = new SseEmitter(0L);
		BoardSubscribers board = boards.computeIfAbsent(scoreboardId,
				ignored -> new BoardSubscribers());
		synchronized (board) {
			board.emitters.add(emitter);
			registerCleanup(scoreboardId, board, emitter);
			send(scoreboardId, board, emitter, "snapshot", nextSignal(scoreboardId));
		}
		return emitter;
	}

	public void publish(UUID scoreboardId, ScoreboardEventType type) {
		BoardSubscribers board = boards.get(scoreboardId);
		if (board == null) {
			return;
		}
		synchronized (board) {
			EventSignal signal = nextSignal(scoreboardId);
			for (SseEmitter emitter : Set.copyOf(board.emitters)) {
				send(scoreboardId, board, emitter, type.apiValue(), signal);
			}
		}
	}

	@Scheduled(fixedDelayString = "${scorebound.live.heartbeat-delay:15000}")
	public void heartbeat() {
		boards.forEach((scoreboardId, board) -> {
			synchronized (board) {
				for (SseEmitter emitter : Set.copyOf(board.emitters)) {
					try {
						emitter.send(SseEmitter.event().comment("keepalive"));
					} catch (IOException | IllegalStateException exception) {
						remove(scoreboardId, board, emitter);
					}
				}
			}
		});
	}

	private void send(UUID scoreboardId, BoardSubscribers board, SseEmitter emitter,
			String eventName, EventSignal signal) {
		try {
			emitter.send(SseEmitter.event()
					.id(Long.toString(signal.sequence()))
					.name(eventName)
					.data(signal));
		} catch (IOException | IllegalStateException exception) {
			remove(scoreboardId, board, emitter);
		}
	}

	private EventSignal nextSignal(UUID scoreboardId) {
		return new EventSignal(sequence.incrementAndGet(), scoreboardId, Instant.now());
	}

	private void registerCleanup(UUID scoreboardId, BoardSubscribers board, SseEmitter emitter) {
		emitter.onCompletion(() -> remove(scoreboardId, board, emitter));
		emitter.onTimeout(() -> remove(scoreboardId, board, emitter));
		emitter.onError(exception -> remove(scoreboardId, board, emitter));
	}

	private void remove(UUID scoreboardId, BoardSubscribers board, SseEmitter emitter) {
		synchronized (board) {
			board.emitters.remove(emitter);
			if (board.emitters.isEmpty()) {
				boards.remove(scoreboardId, board);
			}
		}
	}

	private static final class BoardSubscribers {
		private final Set<SseEmitter> emitters = new LinkedHashSet<>();
	}

	public record EventSignal(long sequence, UUID scoreboardId, Instant occurredAt) {
	}
}

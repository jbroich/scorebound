package de.scorebound.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScoreCancellationRepository extends JpaRepository<ScoreCancellation, UUID> {

	List<ScoreCancellation> findByTransactionIdIn(Collection<UUID> transactionIds);
}

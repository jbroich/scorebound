package de.scorebound.scoring;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoreTransactionRepository extends JpaRepository<ScoreTransaction, UUID> {

	Optional<ScoreTransaction> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);

	Page<ScoreTransaction> findByPeriodId(UUID periodId, Pageable pageable);
}

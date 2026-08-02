package de.scorebound.competition;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetitionPeriodRepository extends JpaRepository<CompetitionPeriod, UUID> {

	List<CompetitionPeriod> findByScoreboardIdOrderByStartsAtDesc(UUID scoreboardId);

	Optional<CompetitionPeriod> findByIdAndScoreboardId(UUID id, UUID scoreboardId);

	Optional<CompetitionPeriod> findByScoreboardIdAndStatus(UUID scoreboardId, PeriodStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select period from CompetitionPeriod period where period.id = :periodId")
	Optional<CompetitionPeriod> findByIdForUpdate(@Param("periodId") UUID periodId);

	@Query("select count(period) > 0 from CompetitionPeriod period "
			+ "where period.scoreboardId = :scoreboardId and period.startsAt < :endsAt "
			+ "and period.endsAt > :startsAt")
	boolean hasOverlap(@Param("scoreboardId") UUID scoreboardId,
			@Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt);

	List<CompetitionPeriod> findByStatusAndEndsAtLessThanEqualAndReopenedAtIsNullOrderByEndsAtAsc(
			PeriodStatus status, Instant now);

	List<CompetitionPeriod> findByStatusAndStartsAtLessThanEqualOrderByStartsAtAsc(
			PeriodStatus status, Instant now);
}

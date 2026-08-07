package de.scorebound.competition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeriodParticipantRepository extends JpaRepository<PeriodParticipant, UUID> {

	List<PeriodParticipant> findByPeriodIdOrderByPositionAsc(UUID periodId);

	Optional<PeriodParticipant> findByPeriodIdAndTeamId(UUID periodId, UUID teamId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select participant from PeriodParticipant participant where participant.periodId = :periodId "
			+ "and participant.teamId = :teamId")
	Optional<PeriodParticipant> findByPeriodIdAndTeamIdForUpdate(
			@Param("periodId") UUID periodId, @Param("teamId") UUID teamId);

	boolean existsByPeriodIdAndTeamId(UUID periodId, UUID teamId);

	@Query("select coalesce(max(participant.position), -1) from PeriodParticipant participant "
			+ "where participant.periodId = :periodId")
	int maximumPosition(@Param("periodId") UUID periodId);
}

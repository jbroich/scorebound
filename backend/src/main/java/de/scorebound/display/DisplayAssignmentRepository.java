package de.scorebound.display;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DisplayAssignmentRepository
		extends JpaRepository<DisplayAssignment, DisplayAssignment.Key> {
	List<DisplayAssignment> findByAccountIdOrderByPositionAsc(UUID accountId);
	boolean existsByAccountIdAndScoreboardId(UUID accountId, UUID scoreboardId);
	void deleteByAccountIdAndScoreboardId(UUID accountId, UUID scoreboardId);

	@Query("select coalesce(max(a.position), -1) from DisplayAssignment a where a.accountId = :accountId")
	int maximumPosition(UUID accountId);
}

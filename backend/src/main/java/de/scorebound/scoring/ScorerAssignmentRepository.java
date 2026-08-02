package de.scorebound.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScorerAssignmentRepository extends JpaRepository<ScorerAssignment, ScorerAssignment.Key> {

	boolean existsByAccountIdAndScoreboardId(UUID accountId, UUID scoreboardId);

	List<ScorerAssignment> findByAccountIdOrderByScoreboardId(UUID accountId);

	void deleteByAccountIdAndScoreboardId(UUID accountId, UUID scoreboardId);
}

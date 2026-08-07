package de.scorebound.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByNormalizedUsername(String normalizedUsername);

	boolean existsByMemberIdAndIdNot(UUID memberId, UUID id);

	List<Account> findAllByOrderByUsernameAsc();
}

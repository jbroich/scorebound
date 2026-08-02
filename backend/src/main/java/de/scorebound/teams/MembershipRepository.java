package de.scorebound.teams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

	Optional<Membership> findByOpenMembershipKey(UUID memberId);

	Optional<Membership> findByMemberIdAndValidUntilIsNull(UUID memberId);

	List<Membership> findByMemberIdOrderByValidFromDesc(UUID memberId);
}

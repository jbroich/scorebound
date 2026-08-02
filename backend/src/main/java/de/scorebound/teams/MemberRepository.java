package de.scorebound.teams;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

	Page<Member> findByActiveTrue(Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select member from Member member where member.id = :memberId")
	Optional<Member> findByIdForUpdate(@Param("memberId") UUID memberId);
}

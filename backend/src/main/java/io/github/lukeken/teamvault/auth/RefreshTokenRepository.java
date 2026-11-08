package io.github.lukeken.teamvault.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/**
	 * SELECT ... FOR UPDATE: two refreshes with the same token serialise, so the second
	 * one reads the row already marked used and takes the reuse path instead of both
	 * rotating. Needs a transaction around it, which the service provides.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** The rows a family revocation has to touch: at most one under normal rotation. */
	List<RefreshToken> findByFamilyIdAndUsedAtIsNullAndRevokedAtIsNull(UUID familyId);

	/**
	 * One statement for the nightly sweep. A derived deleteBy would load every row first;
	 * a client refreshing every 15 minutes leaves 96 rows a day. Status is irrelevant: an
	 * expired row can do nothing whatever its stamps say.
	 */
	@Modifying
	@Query("delete from RefreshToken r where r.expiresAt < :now")
	int deleteExpired(Instant now);

}

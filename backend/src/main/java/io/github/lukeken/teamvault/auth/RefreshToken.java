package io.github.lukeken.teamvault.auth;

import io.github.lukeken.teamvault.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One issued refresh token (ADR-007). Only the hash is stored; the raw token exists in
 * the client's cookie and nowhere else. A row changes exactly twice at most: marked used
 * when it is traded for its successor, and revoked when its family dies. Expiry is fixed
 * at insert as the earlier of the idle lifetime and the family's hard cap.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(nullable = false, updatable = false)
	private UUID familyId;

	@Column(nullable = false, updatable = false, unique = true)
	private String tokenHash;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(nullable = false, updatable = false)
	private Instant familyExpiresAt;

	private Instant usedAt;

	private Instant revokedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(AppUser user, UUID familyId, String tokenHash, Instant expiresAt, Instant familyExpiresAt) {
		this.user = user;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.familyExpiresAt = familyExpiresAt;
	}

	/** Live means it can still be traded: never used, never revoked, not yet expired. */
	public boolean isLive(Instant now) {
		return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public void revoke(Instant now) {
		this.revokedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public AppUser getUser() {
		return user;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getFamilyExpiresAt() {
		return familyExpiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

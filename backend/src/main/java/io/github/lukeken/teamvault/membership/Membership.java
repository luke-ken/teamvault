package io.github.lukeken.teamvault.membership;

import io.github.lukeken.teamvault.company.Company;
import io.github.lukeken.teamvault.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membership")
public class Membership {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Membership() {}

    public Membership(AppUser user, Company company, Role role) {
        this.company = company;
        this.user = user;
        this.role = role;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public Company getCompany() { return company; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}

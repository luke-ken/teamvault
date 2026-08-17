package io.github.lukeken.teamvault.company;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company")
public class Company {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Company() {}

    public Company(String name, Tier tier) {
        this.name = name;
        this.tier = tier;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Tier getTier() { return tier; }
    public Instant getCreatedAt() { return createdAt; }
}

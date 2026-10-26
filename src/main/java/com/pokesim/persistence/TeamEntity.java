package com.pokesim.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "teams")
public class TeamEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;
    @Column(nullable = false)
    private String name;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // The team owns its members: saving/deleting the team saves/deletes them (cascade + orphanRemoval).
    // @BatchSize loads members for up to 20 teams per query, which avoids one query per team on list pages.
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 20)
    private List<TeamMemberEntity> members = new ArrayList<>();

    protected TeamEntity() { }

    public TeamEntity(UUID ownerId, String name) {
        this.ownerId = ownerId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<TeamMemberEntity> getMembers() { return members; }

    public void rename(String newName) { this.name = newName; }
    public void touch() { this.updatedAt = Instant.now(); }
}

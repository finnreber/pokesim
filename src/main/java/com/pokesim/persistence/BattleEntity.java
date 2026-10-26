package com.pokesim.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "battles")
public class BattleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player1_id", nullable = false, updatable = false)
    private UUID player1Id;

    @Column(name = "player2_id")
    private UUID player2Id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BattlePhase phase;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(nullable = false, updatable = false)
    private long seed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private BattleState state;

    /**
     * Optimistic locking. Hibernate adds "WHERE version = ?" to every UPDATE and bumps it. If someone else
     * saved in between, zero rows match and the save fails instead of silently overwriting their change.
     */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BattleEntity() { }

    public BattleEntity(UUID player1Id, long seed, BattleState lobbyState) {
        this.player1Id = player1Id;
        this.seed = seed;
        this.state = lobbyState;
        this.phase = BattlePhase.WAITING_FOR_OPPONENT;
    }

    /** The second player joins: the lobby becomes a real battle. */
    public void start(UUID player2Id, BattleState battleState) {
        this.player2Id = player2Id;
        this.state = battleState;
        this.phase = BattlePhase.AWAITING_ACTIONS;
    }

    public void apply(BattleState newState, BattlePhase newPhase, UUID newWinnerId) {
        this.state = newState;
        this.phase = newPhase;
        this.winnerId = newWinnerId;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPlayer1Id() { return player1Id; }
    public UUID getPlayer2Id() { return player2Id; }
    public BattlePhase getPhase() { return phase; }
    public UUID getWinnerId() { return winnerId; }
    public long getSeed() { return seed; }
    public BattleState getState() { return state; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.pokesim.persistence;

import com.pokesim.engine.BattleEvent;
import com.pokesim.engine.EventType;
import com.pokesim.engine.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One line of a battle's log. Append-only; ordered by id. */
@Entity
@Table(name = "battle_events")
public class BattleEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "battle_id", nullable = false)
    private UUID battleId;
    @Column(nullable = false)
    private int turn;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;
    @Enumerated(EnumType.STRING)
    private Side side;
    @Column(nullable = false)
    private String message;
    @Column(nullable = false)
    private int amount;

    protected BattleEventEntity() { }

    public BattleEventEntity(UUID battleId, int turn, BattleEvent event) {
        this.battleId = battleId;
        this.turn = turn;
        this.type = event.type();
        this.side = event.side();
        this.message = event.message();
        this.amount = event.value();
    }

    public Long getId() { return id; }
    public int getTurn() { return turn; }
    public EventType getType() { return type; }
    public Side getSide() { return side; }
    public String getMessage() { return message; }
    public int getAmount() { return amount; }
}

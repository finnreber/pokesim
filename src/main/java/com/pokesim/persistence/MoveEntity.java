package com.pokesim.persistence;

import com.pokesim.engine.Move;
import com.pokesim.engine.PokemonType;
import com.pokesim.engine.StatusCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "moves")
public class MoveEntity {
    @Id
    private Integer id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private short generation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PokemonType type;
    @Column(nullable = false)
    private short power;
    private Short accuracy;   // null = never misses
    @Column(nullable = false)
    private short pp;
    @Column(nullable = false)
    private short priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCondition ailment;
    @Column(name = "ailment_chance", nullable = false)
    private short ailmentChance;
    @Column(name = "recoil_divisor", nullable = false)
    private short recoilDivisor;

    protected MoveEntity() { }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public int getGeneration() { return generation; }
    public PokemonType getType() { return type; }
    public int getPower() { return power; }
    public Integer getAccuracy() { return accuracy == null ? null : Integer.valueOf(accuracy); }
    public int getPp() { return pp; }
    public int getPriority() { return priority; }
    public StatusCondition getAilment() { return ailment; }
    public int getAilmentChance() { return ailmentChance; }
    public int getRecoilDivisor() { return recoilDivisor; }

    /** Bridge from the database model to the pure engine model. */
    public Move toEngine() {
        return new Move(name, type, power, getAccuracy(), pp, priority, ailment, ailmentChance, recoilDivisor);
    }
}

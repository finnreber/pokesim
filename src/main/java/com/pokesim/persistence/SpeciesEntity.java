package com.pokesim.persistence;

import com.pokesim.engine.PokemonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only reference data. SMALLINT columns map to Java short so Hibernate's schema validation passes. */
@Entity
@Table(name = "pokemon_species")
public class SpeciesEntity {
    @Id
    private Integer id;
    @Column(name = "dex_number", nullable = false)
    private int dexNumber;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private short generation;
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_type", nullable = false)
    private PokemonType primaryType;
    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_type")
    private PokemonType secondaryType;
    @Column(name = "base_hp", nullable = false)
    private short baseHp;
    @Column(name = "base_attack", nullable = false)
    private short baseAttack;
    @Column(name = "base_defense", nullable = false)
    private short baseDefense;
    @Column(name = "base_sp_attack", nullable = false)
    private short baseSpAttack;
    @Column(name = "base_sp_defense", nullable = false)
    private short baseSpDefense;
    @Column(name = "base_speed", nullable = false)
    private short baseSpeed;

    protected SpeciesEntity() { }   // required by JPA

    public Integer getId() { return id; }
    public int getDexNumber() { return dexNumber; }
    public String getName() { return name; }
    public int getGeneration() { return generation; }
    public PokemonType getPrimaryType() { return primaryType; }
    public PokemonType getSecondaryType() { return secondaryType; }
    public int getBaseHp() { return baseHp; }
    public int getBaseAttack() { return baseAttack; }
    public int getBaseDefense() { return baseDefense; }
    public int getBaseSpAttack() { return baseSpAttack; }
    public int getBaseSpDefense() { return baseSpDefense; }
    public int getBaseSpeed() { return baseSpeed; }
}

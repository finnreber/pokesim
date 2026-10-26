package com.pokesim.engine;

/** A known move plus its remaining PP, as plain data. */
public record MoveState(String name, PokemonType type, int power, Integer accuracy, int maxPp, int priority,
                        StatusCondition ailment, int ailmentChance, int recoilDivisor, int pp) { }

package com.pokesim.engine;

import java.util.Objects;

/**
 * Immutable move definition. {@code accuracy == null} means the move never misses.
 * {@code recoilDivisor} of 4 means the user takes 1/4 of the damage dealt (0 = no recoil).
 * Physical/special is NOT stored here: it depends on the generation, so the Ruleset decides.
 */
public record Move(String name, PokemonType type, int power, Integer accuracy, int pp,
                   int priority, StatusCondition ailment, int ailmentChance, int recoilDivisor) {

    public Move {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ailment, "ailment");
        if (power < 0) throw new IllegalArgumentException("power must be >= 0");
        if (pp < 1) throw new IllegalArgumentException("pp must be >= 1");
        if (accuracy != null && (accuracy < 1 || accuracy > 100)) {
            throw new IllegalArgumentException("accuracy must be 1-100 or null");
        }
        if (ailmentChance < 0 || ailmentChance > 100) {
            throw new IllegalArgumentException("ailmentChance must be 0-100");
        }
        if (recoilDivisor < 0) throw new IllegalArgumentException("recoilDivisor must be >= 0");
    }

    public boolean isDamaging() { return power > 0; }

    public static Move damaging(String name, PokemonType type, int power, Integer accuracy, int pp) {
        return new Move(name, type, power, accuracy, pp, 0, StatusCondition.NONE, 0, 0);
    }

    public static Move status(String name, PokemonType type, Integer accuracy, int pp, StatusCondition ailment) {
        return new Move(name, type, 0, accuracy, pp, 0, ailment, 100, 0);
    }

    /** Used automatically when every move is out of PP. */
    public static Move struggle() {
        return new Move("Struggle", PokemonType.TYPELESS, 50, null, 1, 0, StatusCondition.NONE, 0, 4);
    }
}

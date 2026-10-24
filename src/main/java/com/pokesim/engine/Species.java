package com.pokesim.engine;

import java.util.List;
import java.util.Objects;

public record Species(int dexNumber, String name, List<PokemonType> types, StatBlock baseStats) {
    public Species {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseStats, "baseStats");
        types = List.copyOf(types);
        if (types.isEmpty() || types.size() > 2) {
            throw new IllegalArgumentException("A species has 1 or 2 types");
        }
    }
}

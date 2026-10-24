package com.pokesim.engine;

import java.util.EnumMap;
import java.util.Map;

/** Data-only type effectiveness table. Pairs not listed are neutral (1.0). */
public final class TypeChart {
    private final Map<PokemonType, Map<PokemonType, Double>> table;

    private TypeChart(Map<PokemonType, Map<PokemonType, Double>> table) { this.table = table; }

    public double multiplier(PokemonType attacking, PokemonType defending) {
        return table.getOrDefault(attacking, Map.of()).getOrDefault(defending, 1.0);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<PokemonType, Map<PokemonType, Double>> table = new EnumMap<>(PokemonType.class);

        public Builder superEffective(PokemonType atk, PokemonType... defs) { return put(atk, 2.0, defs); }
        public Builder notVeryEffective(PokemonType atk, PokemonType... defs) { return put(atk, 0.5, defs); }
        public Builder noEffect(PokemonType atk, PokemonType... defs) { return put(atk, 0.0, defs); }

        private Builder put(PokemonType atk, double value, PokemonType[] defs) {
            Map<PokemonType, Double> row = table.computeIfAbsent(atk, k -> new EnumMap<>(PokemonType.class));
            for (PokemonType def : defs) row.put(def, value);
            return this;
        }

        public TypeChart build() { return new TypeChart(table); }
    }

    /** Gens 2-5: 17 types (no Fairy), Steel resists Ghost and Dark. Used by FireRed (Gen 3). */
    public static TypeChart gen2to5() {
        PokemonType N = PokemonType.NORMAL, F = PokemonType.FIRE, W = PokemonType.WATER,
                E = PokemonType.ELECTRIC, G = PokemonType.GRASS, I = PokemonType.ICE,
                FI = PokemonType.FIGHTING, PO = PokemonType.POISON, GR = PokemonType.GROUND,
                FL = PokemonType.FLYING, PS = PokemonType.PSYCHIC, B = PokemonType.BUG,
                R = PokemonType.ROCK, GH = PokemonType.GHOST, D = PokemonType.DRAGON,
                DK = PokemonType.DARK, S = PokemonType.STEEL;
        return builder()
                .notVeryEffective(N, R, S).noEffect(N, GH)
                .superEffective(F, G, I, B, S).notVeryEffective(F, F, W, R, D)
                .superEffective(W, F, GR, R).notVeryEffective(W, W, G, D)
                .superEffective(E, W, FL).notVeryEffective(E, E, G, D).noEffect(E, GR)
                .superEffective(G, W, GR, R).notVeryEffective(G, F, G, PO, FL, B, D, S)
                .superEffective(I, G, GR, FL, D).notVeryEffective(I, F, W, I, S)
                .superEffective(FI, N, I, R, DK, S).notVeryEffective(FI, PO, FL, PS, B).noEffect(FI, GH)
                .superEffective(PO, G).notVeryEffective(PO, PO, GR, R, GH).noEffect(PO, S)
                .superEffective(GR, F, E, PO, R, S).notVeryEffective(GR, G, B).noEffect(GR, FL)
                .superEffective(FL, G, FI, B).notVeryEffective(FL, E, R, S)
                .superEffective(PS, FI, PO).notVeryEffective(PS, PS, S).noEffect(PS, DK)
                .superEffective(B, G, PS, DK).notVeryEffective(B, F, FI, PO, FL, GH, S)
                .superEffective(R, F, I, FL, B).notVeryEffective(R, FI, GR, S)
                .superEffective(GH, PS, GH).notVeryEffective(GH, DK, S).noEffect(GH, N)
                .superEffective(D, D).notVeryEffective(D, S)
                .superEffective(DK, PS, GH).notVeryEffective(DK, FI, DK, S)
                .superEffective(S, I, R).notVeryEffective(S, F, W, E, S)
                .build();
    }
}

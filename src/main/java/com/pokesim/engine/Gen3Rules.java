package com.pokesim.engine;

import java.util.Set;

/**
 * Generation 3 mechanics (Ruby/Sapphire/FireRed/LeafGreen/Emerald), simplified:
 * level-scaled stats with fixed IV 31 / EV 0 / neutral nature, no abilities, items or stat stages yet.
 * Physical/special is decided by the move's TYPE (the per-move split only arrived in Gen 4).
 */
public final class Gen3Rules implements Ruleset {
    private static final int IV = 31;
    private static final int EV = 0;
    private static final Set<PokemonType> PHYSICAL = Set.of(
            PokemonType.NORMAL, PokemonType.FIGHTING, PokemonType.FLYING, PokemonType.POISON,
            PokemonType.GROUND, PokemonType.ROCK, PokemonType.BUG, PokemonType.GHOST,
            PokemonType.STEEL, PokemonType.TYPELESS);
    private static final Set<PokemonType> SPECIAL = Set.of(
            PokemonType.FIRE, PokemonType.WATER, PokemonType.GRASS, PokemonType.ELECTRIC,
            PokemonType.PSYCHIC, PokemonType.ICE, PokemonType.DRAGON, PokemonType.DARK);

    private final TypeChart chart = TypeChart.gen2to5();

    @Override public int generation() { return 3; }

    @Override
    public StatBlock computeStats(StatBlock b, int level) {
        return new StatBlock(hp(b.hp(), level), other(b.attack(), level), other(b.defense(), level),
                other(b.spAttack(), level), other(b.spDefense(), level), other(b.speed(), level));
    }

    private int hp(int base, int level) { return (2 * base + IV + EV / 4) * level / 100 + level + 10; }

    private int other(int base, int level) { return (2 * base + IV + EV / 4) * level / 100 + 5; }

    @Override
    public double typeEffectiveness(PokemonType attacking, PokemonType defending) {
        return chart.multiplier(attacking, defending);
    }

    @Override
    public MoveCategory categoryOf(Move move) {
        if (!move.isDamaging()) return MoveCategory.STATUS;
        if (PHYSICAL.contains(move.type())) return MoveCategory.PHYSICAL;
        if (SPECIAL.contains(move.type())) return MoveCategory.SPECIAL;
        throw new IllegalArgumentException("Type " + move.type() + " does not exist in Gen 3");
    }

    @Override
    public boolean canBeAfflicted(BattlePokemon target, StatusCondition status) {
        if (status == StatusCondition.NONE || target.status() != StatusCondition.NONE) return false;
        return switch (status) {
            case BURN -> !target.types().contains(PokemonType.FIRE);
            case FREEZE -> !target.types().contains(PokemonType.ICE);
            case POISON, BADLY_POISONED ->
                    !target.types().contains(PokemonType.POISON) && !target.types().contains(PokemonType.STEEL);
            default -> true;
        };
    }

    @Override public int criticalHitDenominator() { return 16; }
    @Override public int criticalHitMultiplier() { return 2; }
    @Override public double stabMultiplier() { return 1.5; }
    @Override public int fullParalysisChancePercent() { return 25; }
    @Override public double paralysisSpeedMultiplier() { return 0.25; }
    @Override public int thawChancePercent() { return 20; }
    @Override public int minSleepTurns() { return 1; }
    @Override public int maxSleepTurns() { return 4; }

    @Override
    public int residualDamage(BattlePokemon p) {
        return switch (p.status()) {
            case BURN, POISON -> Math.max(1, p.maxHp() / 8);
            case BADLY_POISONED -> Math.max(1, p.maxHp() * Math.min(p.toxicCounter(), 15) / 16);
            default -> 0;
        };
    }
}

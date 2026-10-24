package com.pokesim.engine;

/**
 * Everything that differs between generations lives behind this interface (Strategy pattern).
 * Adding Gen 4 later means writing a new implementation, not editing the engine.
 */
public interface Ruleset {
    int generation();

    StatBlock computeStats(StatBlock base, int level);

    /** Multiplier of one attacking type against ONE defending type (0, 0.5, 1 or 2). */
    double typeEffectiveness(PokemonType attacking, PokemonType defending);

    MoveCategory categoryOf(Move move);

    boolean canBeAfflicted(BattlePokemon target, StatusCondition status);

    int criticalHitDenominator();   // 16 means a 1/16 chance
    int criticalHitMultiplier();
    double stabMultiplier();
    int fullParalysisChancePercent();
    double paralysisSpeedMultiplier();
    int thawChancePercent();
    int minSleepTurns();
    int maxSleepTurns();

    /** HP lost at end of turn from burn/poison/toxic. */
    int residualDamage(BattlePokemon pokemon);
}

package com.pokesim.engine;

/**
 * Pure function of its inputs: no randomness inside. The caller supplies the crit flag and the
 * random roll (85-100), which makes every number hand-verifiable in a unit test.
 */
public final class DamageCalculator {
    private final Ruleset rules;

    public DamageCalculator(Ruleset rules) { this.rules = rules; }

    /** Product of the multiplier against each of the defender's types (e.g. 4.0, 2.0, 0.25, 0). */
    public double effectiveness(Move move, BattlePokemon defender) {
        double result = 1.0;
        for (PokemonType t : defender.types()) result *= rules.typeEffectiveness(move.type(), t);
        return result;
    }

    public int calculate(BattlePokemon attacker, BattlePokemon defender, Move move, boolean crit, int roll) {
        if (!move.isDamaging()) return 0;
        if (effectiveness(move, defender) == 0) return 0;

        MoveCategory category = rules.categoryOf(move);
        boolean physical = category == MoveCategory.PHYSICAL;
        int atk = physical ? attacker.stats().attack() : attacker.stats().spAttack();
        int def = Math.max(1, physical ? defender.stats().defense() : defender.stats().spDefense());

        int damage = (2 * attacker.level() / 5 + 2) * move.power() * atk / def / 50;
        if (physical && attacker.status() == StatusCondition.BURN) damage /= 2;
        damage += 2;
        if (crit) damage *= rules.criticalHitMultiplier();
        damage = damage * roll / 100;
        if (attacker.types().contains(move.type())) damage = (int) Math.floor(damage * rules.stabMultiplier());
        for (PokemonType t : defender.types()) {
            damage = (int) Math.floor(damage * rules.typeEffectiveness(move.type(), t));
        }
        return Math.max(1, damage);
    }
}

package com.pokesim.engine;

import static com.pokesim.engine.Fixtures.mon;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every expected number here is worked out by hand from the Gen 3 formula:
 * base = floor(floor((2L/5 + 2) * Power * A / D) / 50); burn halves; +2; crit x2; roll/100; STAB x1.5; type x.
 */
class DamageCalculatorTest {
    private final DamageCalculator calc = new DamageCalculator(new Gen3Rules());
    private static final Move FLAMETHROWER = Move.damaging("Flamethrower", PokemonType.FIRE, 95, 100, 15);

    // Level 50: (2*50/5 + 2) = 22.  22*95*100/80 = 2612 -> /50 = 52 -> +2 = 54.
    private BattlePokemon fireAttacker() { return mon("Charmeleon", PokemonType.FIRE, 100, FLAMETHROWER); }
    private BattlePokemon grassTarget() {
        // sp. defense 80 instead of the fixture's 100
        Species s = new Species(1, "Oddish", java.util.List.of(PokemonType.GRASS), new StatBlock(1, 1, 1, 1, 1, 1));
        return new BattlePokemon(s, 50, new StatBlock(200, 100, 100, 100, 80, 50), java.util.List.of(Fixtures.TACKLE));
    }

    @Test @DisplayName("max roll: 54 -> STAB 81 -> super effective 162")
    void maxRollStabSuperEffective() {
        assertEquals(162, calc.calculate(fireAttacker(), grassTarget(), FLAMETHROWER, false, 100));
    }

    @Test @DisplayName("min roll floors at each step: 45 -> 67 -> 134")
    void minRoll() {
        assertEquals(134, calc.calculate(fireAttacker(), grassTarget(), FLAMETHROWER, false, 85));
    }

    @Test @DisplayName("critical hit doubles after the +2: 54 -> 108 -> 162 -> 324")
    void critical() {
        assertEquals(324, calc.calculate(fireAttacker(), grassTarget(), FLAMETHROWER, true, 100));
    }

    @Test @DisplayName("no STAB when the attacker's type differs from the move's type")
    void noStab() {
        BattlePokemon normalAttacker = mon("Rattata", PokemonType.NORMAL, 100, FLAMETHROWER);
        assertEquals(108, calc.calculate(normalAttacker, grassTarget(), FLAMETHROWER, false, 100));
    }

    @Test @DisplayName("burn halves PHYSICAL damage: 35 -> 17 -> 19 instead of 37")
    void burnHalvesPhysical() {
        Move karateChop = Move.damaging("Karate Chop", PokemonType.FIGHTING, 80, 100, 25);
        BattlePokemon attacker = mon("Machop", PokemonType.NORMAL, 100, karateChop);
        BattlePokemon target = mon("Squirtle", PokemonType.WATER, 100, Fixtures.TACKLE);
        assertEquals(37, calc.calculate(attacker, target, karateChop, false, 100));
        attacker.setStatus(StatusCondition.BURN);
        assertEquals(19, calc.calculate(attacker, target, karateChop, false, 100));
    }

    @Test @DisplayName("burn does NOT halve special damage")
    void burnIgnoredForSpecial() {
        BattlePokemon attacker = fireAttacker();
        attacker.setStatus(StatusCondition.BURN);
        assertEquals(162, calc.calculate(attacker, grassTarget(), FLAMETHROWER, false, 100));
    }

    @Test @DisplayName("immunity deals exactly 0, not the usual minimum of 1")
    void immunity() {
        BattlePokemon ghost = mon("Gastly", PokemonType.GHOST, 50, Fixtures.TACKLE);
        BattlePokemon attacker = mon("Rattata", PokemonType.NORMAL, 50, Fixtures.TACKLE);
        assertEquals(0, calc.calculate(attacker, ghost, Fixtures.TACKLE, false, 100));
    }

    @Test @DisplayName("a heavily resisted hit still does at least 1 damage")
    void minimumOneDamage() {
        Move weakGrass = Move.damaging("Absorb", PokemonType.GRASS, 40, 100, 20);
        Species fireFlying = new Species(6, "Charizard", java.util.List.of(PokemonType.FIRE, PokemonType.FLYING),
                new StatBlock(1, 1, 1, 1, 1, 1));
        BattlePokemon target = new BattlePokemon(fireFlying, 50, new StatBlock(200, 100, 100, 100, 500, 50),
                java.util.List.of(Fixtures.TACKLE));
        Species weak = new Species(1, "Weakling", java.util.List.of(PokemonType.NORMAL), new StatBlock(1, 1, 1, 1, 1, 1));
        BattlePokemon attacker = new BattlePokemon(weak, 50, new StatBlock(200, 1, 1, 1, 1, 50), java.util.List.of(weakGrass));
        assertEquals(0.25, calc.effectiveness(weakGrass, target));
        assertEquals(1, calc.calculate(attacker, target, weakGrass, false, 85));
    }

    @Test @DisplayName("dual types multiply: Rock vs Fire/Flying is 4x")
    void dualTypeMultiplier() {
        Move rockThrow = Move.damaging("Rock Throw", PokemonType.ROCK, 50, 90, 15);
        Species s = new Species(6, "Charizard", java.util.List.of(PokemonType.FIRE, PokemonType.FLYING), new StatBlock(1, 1, 1, 1, 1, 1));
        BattlePokemon target = new BattlePokemon(s, 50, new StatBlock(200, 100, 100, 100, 100, 50), java.util.List.of(Fixtures.TACKLE));
        assertEquals(4.0, calc.effectiveness(rockThrow, target));
    }
}

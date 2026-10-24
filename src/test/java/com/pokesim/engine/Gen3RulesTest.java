package com.pokesim.engine;

import static com.pokesim.engine.Fixtures.mon;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Gen3RulesTest {
    private final Gen3Rules rules = new Gen3Rules();

    @Test @DisplayName("Pikachu at level 50 (IV 31, EV 0, neutral nature) has the in-game stats")
    void pikachuStats() {
        StatBlock pikachu = new StatBlock(35, 55, 40, 50, 50, 90);
        StatBlock s = rules.computeStats(pikachu, 50);
        assertEquals(110, s.hp());      // floor((70+31)*50/100)=50, +50+10
        assertEquals(75, s.attack());   // floor((110+31)*50/100)=70, +5
        assertEquals(110, s.speed());   // floor((180+31)*50/100)=105, +5
    }

    @Test @DisplayName("physical/special is decided by move TYPE in Gen 3")
    void categoryByType() {
        assertEquals(MoveCategory.SPECIAL, rules.categoryOf(Move.damaging("Flamethrower", PokemonType.FIRE, 95, 100, 15)));
        assertEquals(MoveCategory.PHYSICAL, rules.categoryOf(Move.damaging("Shadow Ball", PokemonType.GHOST, 80, 100, 15)));
        assertEquals(MoveCategory.STATUS, rules.categoryOf(Move.status("Thunder Wave", PokemonType.ELECTRIC, 100, 20, StatusCondition.PARALYSIS)));
    }

    @Test @DisplayName("types that do not exist in Gen 3 are rejected, not silently accepted")
    void fairyMoveRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> rules.categoryOf(Move.damaging("Moonblast", PokemonType.FAIRY, 95, 100, 15)));
    }

    @Test @DisplayName("type-based status immunities and one-status-at-a-time")
    void statusImmunities() {
        BattlePokemon fire = mon("Charmander", PokemonType.FIRE, 50, Fixtures.TACKLE);
        BattlePokemon ice = mon("Jynx", PokemonType.ICE, 50, Fixtures.TACKLE);
        BattlePokemon steel = mon("Magnemite", PokemonType.STEEL, 50, Fixtures.TACKLE);
        BattlePokemon normal = mon("Rattata", PokemonType.NORMAL, 50, Fixtures.TACKLE);
        assertFalse(rules.canBeAfflicted(fire, StatusCondition.BURN));
        assertFalse(rules.canBeAfflicted(ice, StatusCondition.FREEZE));
        assertFalse(rules.canBeAfflicted(steel, StatusCondition.POISON));
        assertTrue(rules.canBeAfflicted(normal, StatusCondition.BURN));
        normal.setStatus(StatusCondition.SLEEP);
        assertFalse(rules.canBeAfflicted(normal, StatusCondition.BURN));
    }

    @Test @DisplayName("toxic damage grows by 1/16 of max HP each turn")
    void toxicEscalates() {
        BattlePokemon p = mon("Rattata", PokemonType.NORMAL, 50, Fixtures.TACKLE); // 200 HP
        p.setStatus(StatusCondition.BADLY_POISONED);
        p.incrementToxic();
        assertEquals(12, rules.residualDamage(p));
        p.incrementToxic();
        assertEquals(25, rules.residualDamage(p));
    }
}

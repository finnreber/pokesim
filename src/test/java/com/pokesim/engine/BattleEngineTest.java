package com.pokesim.engine;

import static com.pokesim.engine.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each test scripts the RNG, so outcomes are exact. "15, 15" = no crit + max damage roll
 * (see ScriptedRandom). Speeds are chosen so turn order is unambiguous unless a test is about ties.
 */
class BattleEngineTest {
    private final Gen3Rules rules = new Gen3Rules();

    private BattleEngine engine(ScriptedRandom rng) { return new BattleEngine(rules, rng); }

    // ---------------------------------------------------------------- turn order

    @Test @DisplayName("the faster Pokemon moves first")
    void fasterMovesFirst() {
        Battle b = battle(mon("Fast", PokemonType.NORMAL, 100, TACKLE), mon("Slow", PokemonType.NORMAL, 50, TACKLE));
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15, 15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(List.of(Side.P1, Side.P2), sidesOf(events, EventType.MOVE_USED));
    }

    @Test @DisplayName("a higher-priority move beats a faster opponent")
    void priorityBeatsSpeed() {
        Battle b = battle(mon("Slow", PokemonType.NORMAL, 50, QUICK_ATTACK), mon("Fast", PokemonType.NORMAL, 100, TACKLE));
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15, 15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(List.of(Side.P1, Side.P2), sidesOf(events, EventType.MOVE_USED));
    }

    @Test @DisplayName("equal speed is a coin flip decided by the RandomSource")
    void speedTie() {
        Battle b = battle(mon("A", PokemonType.NORMAL, 80, TACKLE), mon("B", PokemonType.NORMAL, 80, TACKLE));
        ScriptedRandom rng = new ScriptedRandom(1, 15, 15, 15, 15); // 1 => second listed (P2) goes first
        List<BattleEvent> events = engine(rng).resolveTurn(b, move(0), move(0));
        assertEquals(List.of(Side.P2, Side.P1), sidesOf(events, EventType.MOVE_USED));
        rng.assertAllUsed();
    }

    @Test @DisplayName("paralysis cuts speed to 1/4, so a slower opponent now outspeeds it")
    void paralysisSlowsPokemon() {
        BattlePokemon paralyzed = mon("Jolteon", PokemonType.ELECTRIC, 100, TACKLE);
        paralyzed.setStatus(StatusCondition.PARALYSIS);
        Battle b = battle(paralyzed, mon("Slowpoke", PokemonType.NORMAL, 50, TACKLE));
        // P2 acts first (15,15); then P1 passes the paralysis check with 99 (>= 25), then 15,15
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15, 99, 15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(List.of(Side.P2, Side.P1), sidesOf(events, EventType.MOVE_USED));
    }

    @Test @DisplayName("a switch resolves before the opponent's move, which then hits the newcomer")
    void switchResolvesFirst() {
        BattlePokemon lead = mon("Lead", PokemonType.NORMAL, 50, TACKLE);
        BattlePokemon bench = mon("Bench", PokemonType.NORMAL, 50, TACKLE);
        Battle b = battle(List.of(lead, bench), List.of(mon("Foe", PokemonType.NORMAL, 999, TACKLE)));
        engine(new ScriptedRandom(15, 15)).resolveTurn(b, new Action.Switch(1), move(0));
        assertEquals(200, lead.currentHp());
        assertTrue(bench.currentHp() < 200);
        assertEquals(1, b.side(Side.P1).activeIndex());
    }

    // ---------------------------------------------------------------- hit, miss, immunity

    @Test @DisplayName("a missed move deals no damage")
    void miss() {
        Move shaky = Move.damaging("Shaky", PokemonType.NORMAL, 40, 50, 10);
        BattlePokemon foe = mon("Foe", PokemonType.NORMAL, 10, TACKLE);
        Battle b = battle(mon("Me", PokemonType.NORMAL, 100, shaky), foe);
        // accuracy draw 70 >= 50 => miss; then the foe's Tackle: 15, 15
        List<BattleEvent> events = engine(new ScriptedRandom(70, 15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(1, count(events, EventType.MISSED));
        assertEquals(200, foe.currentHp());
    }

    @Test @DisplayName("an immune target takes nothing and the engine draws no crit/roll numbers")
    void immuneTarget() {
        BattlePokemon ghost = mon("Gastly", PokemonType.GHOST, 10, TACKLE);
        Battle b = battle(mon("Me", PokemonType.NORMAL, 100, TACKLE), ghost);
        ScriptedRandom rng = new ScriptedRandom(15, 15); // only the ghost's Tackle on me needs numbers
        List<BattleEvent> events = engine(rng).resolveTurn(b, move(0), move(0));
        assertEquals(1, count(events, EventType.NO_EFFECT));
        assertEquals(200, ghost.currentHp());
        rng.assertAllUsed();
    }

    @Test @DisplayName("damage dealt equals the calculator's number and effectiveness is reported")
    void damageMatchesCalculator() {
        Move water = Move.damaging("Water Gun", PokemonType.WATER, 40, 100, 25);
        BattlePokemon me = mon("Me", PokemonType.WATER, 100, water);
        BattlePokemon foe = mon("Foe", PokemonType.FIRE, 10, TACKLE);
        Battle b = battle(me, foe);
        int expected = new DamageCalculator(rules).calculate(me, foe, water, false, 100);
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15, 15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(expected, first(events, EventType.DAMAGE).value());
        assertEquals(1, count(events, EventType.SUPER_EFFECTIVE));
        assertEquals(200 - expected, foe.currentHp());
    }

    // ---------------------------------------------------------------- status

    @Test @DisplayName("burn costs 1/8 max HP at end of turn")
    void burnResidual() {
        BattlePokemon burned = mon("Burned", PokemonType.NORMAL, 100, TACKLE);
        burned.setStatus(StatusCondition.BURN);
        Battle b = battle(burned, mon("Foe", PokemonType.NORMAL, 50, TACKLE));
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15, 15, 15)).resolveTurn(b, move(0), move(0));
        BattleEvent residual = first(events, EventType.RESIDUAL_DAMAGE);
        assertEquals(Side.P1, residual.side());
        assertEquals(25, residual.value());
    }

    @Test @DisplayName("toxic damage escalates turn over turn: 12, then 25")
    void toxicEscalation() {
        BattlePokemon poisoned = mon("Poisoned", PokemonType.NORMAL, 100, TACKLE);
        poisoned.setStatus(StatusCondition.BADLY_POISONED);
        Battle b = battle(poisoned, mon("Foe", PokemonType.NORMAL, 50, TACKLE));
        BattleEngine e = engine(new ScriptedRandom(15, 15, 15, 15, 15, 15, 15, 15));
        assertEquals(12, first(e.resolveTurn(b, move(0), move(0)), EventType.RESIDUAL_DAMAGE).value());
        assertEquals(25, first(e.resolveTurn(b, move(0), move(0)), EventType.RESIDUAL_DAMAGE).value());
    }

    @Test @DisplayName("sleep with 2 turns left: lose two turns, then wake up and act on the third")
    void sleepDuration() {
        BattlePokemon sleeper = mon("Sleeper", PokemonType.NORMAL, 100, TACKLE);
        sleeper.setStatus(StatusCondition.SLEEP);
        sleeper.setSleepTurns(2);
        Battle b = battle(sleeper, mon("Foe", PokemonType.NORMAL, 50, TACKLE));
        ScriptedRandom rng = new ScriptedRandom(15, 15, 15, 15, 15, 15, 15, 15);
        BattleEngine e = engine(rng);

        assertEquals(1, count(e.resolveTurn(b, move(0), move(0)), EventType.MOVE_USED)); // only the foe moved
        assertEquals(1, count(e.resolveTurn(b, move(0), move(0)), EventType.MOVE_USED));
        List<BattleEvent> third = e.resolveTurn(b, move(0), move(0));
        assertEquals(1, count(third, EventType.WOKE_UP));
        assertEquals(2, count(third, EventType.MOVE_USED));
        assertEquals(StatusCondition.NONE, sleeper.status());
        rng.assertAllUsed();
    }

    @Test @DisplayName("full paralysis stops the move and uses no PP")
    void fullParalysis() {
        BattlePokemon para = mon("Para", PokemonType.NORMAL, 100, TACKLE);
        para.setStatus(StatusCondition.PARALYSIS);
        BattlePokemon foe = mon("Foe", PokemonType.NORMAL, 10, TACKLE);
        Battle b = battle(para, foe);
        // para speed 25 vs foe 10 => para first; draw 10 (< 25) => fully paralyzed; then foe attacks
        engine(new ScriptedRandom(10, 15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(200, foe.currentHp());
        assertEquals(35, para.moves().get(0).pp());
    }

    @Test @DisplayName("a status move inflicts its condition")
    void statusMoveInflicts() {
        Move wave = Move.status("Thunder Wave", PokemonType.ELECTRIC, 100, 20, StatusCondition.PARALYSIS);
        BattlePokemon foe = mon("Foe", PokemonType.NORMAL, 10, TACKLE);
        Battle b = battle(mon("Me", PokemonType.ELECTRIC, 100, wave), foe);
        engine(new ScriptedRandom(15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(StatusCondition.PARALYSIS, foe.status());
    }

    @Test @DisplayName("a Fire-type cannot be burned: the move fails")
    void fireImmuneToBurn() {
        Move willOWisp = Move.status("Will-O-Wisp", PokemonType.FIRE, 100, 15, StatusCondition.BURN);
        BattlePokemon foe = mon("Foe", PokemonType.FIRE, 10, TACKLE);
        Battle b = battle(mon("Me", PokemonType.NORMAL, 100, willOWisp), foe);
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(1, count(events, EventType.FAILED));
        assertEquals(StatusCondition.NONE, foe.status());
    }

    @Test @DisplayName("a 10% secondary effect triggers on a low draw and not on a high one")
    void secondaryEffectChance() {
        Move ember = new Move("Ember", PokemonType.FIRE, 40, 100, 25, 0, StatusCondition.BURN, 10, 0);
        BattlePokemon hit = mon("Foe1", PokemonType.NORMAL, 10, TACKLE);
        engine(new ScriptedRandom(15, 15, 5, 15, 15))
                .resolveTurn(battle(mon("Me", PokemonType.FIRE, 100, ember), hit), move(0), move(0));
        assertEquals(StatusCondition.BURN, hit.status());

        BattlePokemon spared = mon("Foe2", PokemonType.NORMAL, 10, TACKLE);
        engine(new ScriptedRandom(15, 15, 50, 15, 15))
                .resolveTurn(battle(mon("Me", PokemonType.FIRE, 100, ember), spared), move(0), move(0));
        assertEquals(StatusCondition.NONE, spared.status());
    }

    // ---------------------------------------------------------------- PP, struggle, validation

    @Test @DisplayName("with no PP left the user Struggles and takes 1/4 recoil")
    void struggle() {
        Move oneShot = Move.damaging("One Shot", PokemonType.NORMAL, 40, 100, 1);
        BattlePokemon me = mon("Me", PokemonType.NORMAL, 100, oneShot);
        me.moves().get(0).usePp();
        Battle b = battle(me, mon("Foe", PokemonType.NORMAL, 10, TACKLE));
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15, 15, 15)).resolveTurn(b, move(0), move(0));
        int dealt = first(events, EventType.DAMAGE).value();
        assertEquals(Math.max(1, dealt / 4), first(events, EventType.RECOIL).value());
        assertTrue(events.stream().anyMatch(e -> e.message().contains("Struggle")));
    }

    @Test @DisplayName("illegal actions are rejected before anything changes")
    void validation() {
        BattlePokemon fainted = mon("Fainted", PokemonType.NORMAL, 1, 10, TACKLE);
        fainted.takeDamage(1);
        Battle b = battle(List.of(mon("A", PokemonType.NORMAL, 50, TACKLE), mon("B", PokemonType.NORMAL, 50, TACKLE), fainted),
                List.of(mon("Foe", PokemonType.NORMAL, 50, TACKLE)));
        BattleEngine e = engine(new ScriptedRandom());
        assertThrows(IllegalActionException.class, () -> e.resolveTurn(b, move(4), move(0)));
        assertThrows(IllegalActionException.class, () -> e.resolveTurn(b, new Action.Switch(0), move(0)));
        assertThrows(IllegalActionException.class, () -> e.resolveTurn(b, new Action.Switch(2), move(0)));
        assertThrows(IllegalActionException.class, () -> e.resolveTurn(b, new Action.Switch(9), move(0)));
        assertThrows(IllegalActionException.class, () -> e.resolveTurn(b, null, move(0)));
        assertEquals(0, b.turn());
    }

    // ---------------------------------------------------------------- fainting and winning

    @Test @DisplayName("a KO skips the victim's action and asks for a replacement")
    void faintRequiresReplacement() {
        BattlePokemon fragile = mon("Fragile", PokemonType.NORMAL, 1, 10, TACKLE);
        BattlePokemon backup = mon("Backup", PokemonType.NORMAL, 10, TACKLE);
        Battle b = battle(List.of(mon("Me", PokemonType.NORMAL, 100, TACKLE)), List.of(fragile, backup));
        BattleEngine e = engine(new ScriptedRandom(15, 15)); // only the attacker draws: victim never acts

        List<BattleEvent> events = e.resolveTurn(b, move(0), move(0));
        assertEquals(1, count(events, EventType.FAINTED));
        assertEquals(1, count(events, EventType.MOVE_USED));
        assertEquals(BattleStatus.AWAITING_REPLACEMENT, b.status());
        assertTrue(b.pendingReplacements().contains(Side.P2));

        assertThrows(IllegalActionException.class, () -> e.resolveTurn(b, move(0), move(0)));
        assertThrows(IllegalActionException.class, () -> e.replaceFainted(b, Side.P2, 0)); // fainted
        assertThrows(IllegalActionException.class, () -> e.replaceFainted(b, Side.P1, 0)); // not pending

        e.replaceFainted(b, Side.P2, 1);
        assertEquals(BattleStatus.AWAITING_ACTIONS, b.status());
        assertEquals("Backup", b.side(Side.P2).active().name());
    }

    @Test @DisplayName("knocking out the last Pokemon ends the battle with a winner")
    void victory() {
        Battle b = battle(mon("Me", PokemonType.NORMAL, 100, TACKLE), mon("Foe", PokemonType.NORMAL, 1, 10, TACKLE));
        List<BattleEvent> events = engine(new ScriptedRandom(15, 15)).resolveTurn(b, move(0), move(0));
        assertEquals(BattleStatus.FINISHED, b.status());
        assertEquals(Side.P1, b.winner());
        assertEquals(1, count(events, EventType.BATTLE_END));
        assertThrows(IllegalActionException.class,
                () -> engine(new ScriptedRandom()).resolveTurn(b, move(0), move(0)));
    }

    @Test @DisplayName("both sides losing their last Pokemon in one turn is a draw")
    void draw() {
        // Both are poisoned with 1 HP and use a harmless move: residual damage KOs both at once.
        Move splash = Move.status("Splash", PokemonType.NORMAL, null, 40, StatusCondition.NONE);
        BattlePokemon x = mon("X", PokemonType.NORMAL, 1, 100, splash);
        BattlePokemon y = mon("Y", PokemonType.NORMAL, 1, 50, splash);
        x.setStatus(StatusCondition.POISON);
        y.setStatus(StatusCondition.POISON);
        Battle b = battle(x, y);
        engine(new ScriptedRandom()).resolveTurn(b, move(0), move(0));
        assertEquals(BattleStatus.FINISHED, b.status());
        assertNull(b.winner());
    }

    // ---------------------------------------------------------------- determinism

    @Test @DisplayName("the same seed produces the same battle, event for event")
    void sameSeedSameBattle() {
        List<BattleEvent> run1 = playToTheEnd(42L);
        List<BattleEvent> run2 = playToTheEnd(42L);
        assertEquals(run1, run2);
        assertTrue(run1.size() > 5);
    }

    private List<BattleEvent> playToTheEnd(long seed) {
        Move flame = new Move("Ember", PokemonType.FIRE, 40, 100, 25, 0, StatusCondition.BURN, 10, 0);
        Battle b = battle(mon("A", PokemonType.FIRE, 120, flame, TACKLE), mon("B", PokemonType.NORMAL, 100, flame, TACKLE));
        BattleEngine e = new BattleEngine(rules, new JavaRandomSource(seed));
        List<BattleEvent> all = new java.util.ArrayList<>();
        while (b.status() == BattleStatus.AWAITING_ACTIONS) all.addAll(e.resolveTurn(b, move(0), move(1)));
        return all;
    }
}

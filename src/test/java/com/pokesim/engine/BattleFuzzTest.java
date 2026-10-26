package com.pokesim.engine;

import static com.pokesim.engine.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Property-style test: instead of hand-picking scenarios, play 400 random battles (random teams, moves, stats,
 * statuses, switches) and check invariants that must hold after EVERY turn. Finds bugs nobody thought to test for.
 */
class BattleFuzzTest {
    private final Gen3Rules rules = new Gen3Rules();
    private static final PokemonType[] TYPES = {PokemonType.NORMAL, PokemonType.FIRE, PokemonType.WATER, PokemonType.GRASS,
            PokemonType.ELECTRIC, PokemonType.GHOST, PokemonType.STEEL, PokemonType.ICE, PokemonType.POISON, PokemonType.DARK};

    private Move randomMove(Random r, boolean forceDamaging) {
        PokemonType t = TYPES[r.nextInt(TYPES.length)];
        int kind = forceDamaging ? 0 : r.nextInt(5);
        int pp = 1 + r.nextInt(12);
        return switch (kind) {
            case 0, 1 -> Move.damaging("D" + r.nextInt(999), t, 20 + r.nextInt(100), r.nextBoolean() ? null : 60 + r.nextInt(41), pp);
            case 2 -> new Move("R" + r.nextInt(999), t, 60 + r.nextInt(60), 100, pp, r.nextInt(3) - 1, StatusCondition.NONE, 0, 3 + r.nextInt(2));
            case 3 -> new Move("S" + r.nextInt(999), t, 30 + r.nextInt(60), 100, pp, 0,
                    StatusCondition.values()[1 + r.nextInt(5)], 1 + r.nextInt(100), 0);
            default -> Move.status("T" + r.nextInt(999), t, r.nextBoolean() ? null : 70 + r.nextInt(31), pp,
                    StatusCondition.values()[1 + r.nextInt(5)]);
        };
    }

    private BattleSide randomSide(Side id, Random r) {
        List<BattlePokemon> team = new ArrayList<>();
        int size = 1 + r.nextInt(6);
        for (int i = 0; i < size; i++) {
            List<Move> moves = new ArrayList<>(List.of(randomMove(r, true)));
            for (int m = r.nextInt(4); m > 0; m--) moves.add(randomMove(r, false));
            PokemonType t = TYPES[r.nextInt(TYPES.length)];
            team.add(mon("M" + i, t, 1 + r.nextInt(150), 20 + r.nextInt(180), moves.toArray(new Move[0])));
        }
        return new BattleSide(id, team);
    }

    private Action choose(Battle b, Side side, Random r) {
        BattleSide s = b.side(side);
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < s.team().size(); i++) if (!s.team().get(i).isFainted() && i != s.activeIndex()) alive.add(i);
        if (!alive.isEmpty() && r.nextInt(8) == 0) return new Action.Switch(alive.get(r.nextInt(alive.size())));
        BattlePokemon active = s.active();
        List<Integer> usable = new ArrayList<>();
        for (int i = 0; i < active.moves().size(); i++) if (active.moves().get(i).pp() > 0) usable.add(i);
        return move(usable.isEmpty() ? 0 : usable.get(r.nextInt(usable.size())));
    }

    private void assertInvariants(Battle b, long seed) {
        for (Side side : Side.values()) {
            for (BattlePokemon p : b.side(side).team()) {
                String ctx = "seed " + seed + " " + side + " " + p.name();
                assertTrue(p.currentHp() >= 0 && p.currentHp() <= p.maxHp(), "HP out of range, " + ctx);
                if (p.status() != StatusCondition.SLEEP) assertEquals(0, p.sleepTurns(), "stray sleep counter, " + ctx);
                if (p.status() != StatusCondition.BADLY_POISONED) assertEquals(0, p.toxicCounter(), "stray toxic counter, " + ctx);
                for (MoveSlot m : p.moves()) assertTrue(m.pp() >= 0 && m.pp() <= m.move().pp(), "PP out of range, " + ctx);
            }
        }
        switch (b.status()) {
            case AWAITING_ACTIONS -> {
                assertTrue(b.pendingReplacements().isEmpty(), "pending replacements while awaiting actions, seed " + seed);
                for (Side s : Side.values()) assertFalse(b.side(s).active().isFainted(), "fainted active while awaiting actions, seed " + seed);
            }
            case AWAITING_REPLACEMENT -> {
                assertFalse(b.pendingReplacements().isEmpty(), "replacement phase with nobody pending, seed " + seed);
                for (Side s : b.pendingReplacements()) assertTrue(b.side(s).active().isFainted(), "pending side not fainted, seed " + seed);
            }
            case FINISHED -> {
                boolean p1Out = !b.side(Side.P1).hasUsablePokemon(), p2Out = !b.side(Side.P2).hasUsablePokemon();
                assertTrue(p1Out || p2Out, "finished but both sides still have Pokemon, seed " + seed);
                if (b.winner() != null) assertEquals(p1Out ? Side.P2 : Side.P1, b.winner(), "wrong winner, seed " + seed);
                else assertTrue(p1Out && p2Out, "draw without both sides out, seed " + seed);
            }
        }
    }

    @Test @DisplayName("400 random battles all finish, and the invariants hold after every turn")
    void randomBattlesNeverBreakInvariants() {
        for (long seed = 0; seed < 400; seed++) {
            Random choices = new Random(seed);
            Battle b = new Battle(randomSide(Side.P1, choices), randomSide(Side.P2, choices));
            BattleEngine engine = new BattleEngine(rules, new JavaRandomSource(seed));
            int guard = 0;
            while (b.status() != BattleStatus.FINISHED) {
                assertTrue(++guard < 2000, "battle did not finish, seed " + seed);
                if (b.status() == BattleStatus.AWAITING_REPLACEMENT) {
                    for (Side s : List.copyOf(b.pendingReplacements())) {
                        List<Integer> alive = new ArrayList<>();
                        for (int i = 0; i < b.side(s).team().size(); i++) if (!b.side(s).team().get(i).isFainted()) alive.add(i);
                        engine.replaceFainted(b, s, alive.get(choices.nextInt(alive.size())));
                    }
                } else {
                    engine.resolveTurn(b, choose(b, Side.P1, choices), choose(b, Side.P2, choices));
                }
                assertInvariants(b, seed);
            }
        }
    }

    @Test @DisplayName("snapshotting mid-battle at random points never changes the outcome")
    void snapshotsAreTransparent() {
        for (long seed = 0; seed < 100; seed++) {
            Random c1 = new Random(seed), c2 = new Random(seed);
            Battle plain = new Battle(randomSide(Side.P1, c1), randomSide(Side.P2, c1));
            Battle snapped = new Battle(randomSide(Side.P1, c2), randomSide(Side.P2, c2));
            BattleEngine e1 = new BattleEngine(rules, new JavaRandomSource(seed)), e2 = new BattleEngine(rules, new JavaRandomSource(seed));
            int guard = 0;
            while (plain.status() != BattleStatus.FINISHED && ++guard < 2000) {
                if (plain.status() == BattleStatus.AWAITING_REPLACEMENT) {
                    for (Side s : List.copyOf(plain.pendingReplacements())) {
                        int idx = -1;
                        for (int i = 0; i < plain.side(s).team().size() && idx < 0; i++) if (!plain.side(s).team().get(i).isFainted()) idx = i;
                        e1.replaceFainted(plain, s, idx);
                        e2.replaceFainted(snapped, s, idx);
                    }
                } else {
                    Action a1 = choose(plain, Side.P1, c1), a2 = choose(plain, Side.P2, c1);
                    choose(snapped, Side.P1, c2); choose(snapped, Side.P2, c2);   // keep both choice streams in step
                    e1.resolveTurn(plain, a1, a2);
                    e2.resolveTurn(snapped, a1, a2);
                }
                snapped = BattleSnapshots.restore(BattleSnapshots.capture(snapped));   // save + load after EVERY step
                assertEquals(BattleSnapshots.capture(plain), BattleSnapshots.capture(snapped), "diverged, seed " + seed);
            }
        }
    }
}

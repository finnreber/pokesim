package com.pokesim.engine;

import static com.pokesim.engine.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BattleSnapshotTest {
    private final Gen3Rules rules = new Gen3Rules();
    private static final Move EMBER = new Move("Ember", PokemonType.FIRE, 40, 100, 25, 0, StatusCondition.BURN, 30, 0);
    private static final Move SPORE = Move.status("Spore", PokemonType.GRASS, 100, 15, StatusCondition.SLEEP);

    private Battle midBattle() {
        Battle b = battle(List.of(mon("A1", PokemonType.FIRE, 100, EMBER, TACKLE), mon("A2", PokemonType.NORMAL, 60, TACKLE)),
                List.of(mon("B1", PokemonType.GRASS, 80, SPORE, TACKLE), mon("B2", PokemonType.WATER, 70, TACKLE)));
        BattleEngine e = new BattleEngine(rules, new JavaRandomSource(7));
        e.resolveTurn(b, move(0), move(0));
        e.resolveTurn(b, move(1), move(1));
        return b;
    }

    @Test @DisplayName("capture then restore gives back exactly the same state")
    void roundTrip() {
        Battle original = midBattle();
        BattleSnapshot snapshot = BattleSnapshots.capture(original);
        assertEquals(snapshot, BattleSnapshots.capture(BattleSnapshots.restore(snapshot)));
    }

    @Test @DisplayName("a restored battle plays out identically to the original (nothing was lost in the snapshot)")
    void restoredBattleBehavesIdentically() {
        Battle original = midBattle();
        Battle restored = BattleSnapshots.restore(BattleSnapshots.capture(original));

        BattleEngine e1 = new BattleEngine(rules, new JavaRandomSource(99));
        BattleEngine e2 = new BattleEngine(rules, new JavaRandomSource(99));
        List<BattleEvent> log1 = new ArrayList<>(), log2 = new ArrayList<>();
        for (int i = 0; i < 40 && original.status() == BattleStatus.AWAITING_ACTIONS; i++) {
            log1.addAll(e1.resolveTurn(original, move(0), move(1)));
            log2.addAll(e2.resolveTurn(restored, move(0), move(1)));
        }
        assertEquals(log1, log2);
        assertEquals(BattleSnapshots.capture(original), BattleSnapshots.capture(restored));
    }

    @Test @DisplayName("PP, status and HP survive the round trip")
    void mutableStateIsKept() {
        Battle b = battle(mon("A", PokemonType.NORMAL, 100, TACKLE), mon("B", PokemonType.NORMAL, 50, TACKLE));
        BattlePokemon a = b.side(Side.P1).active();
        a.moves().get(0).usePp();
        a.takeDamage(37);
        a.setStatus(StatusCondition.SLEEP);
        a.setSleepTurns(3);
        BattlePokemon back = BattleSnapshots.restore(BattleSnapshots.capture(b)).side(Side.P1).active();
        assertEquals(34, back.moves().get(0).pp());
        assertEquals(163, back.currentHp());
        assertEquals(StatusCondition.SLEEP, back.status());
        assertEquals(3, back.sleepTurns());
    }

    @Test @DisplayName("a lobby snapshot (no second player) cannot be restored as a battle")
    void lobbyCannotBeRestored() {
        SideSnapshot p1 = BattleSnapshots.captureSide(new BattleSide(Side.P1, List.of(mon("A", PokemonType.NORMAL, 50, TACKLE))));
        BattleSnapshot lobby = new BattleSnapshot(p1, null, 0, BattleStatus.AWAITING_ACTIONS, List.of(), null);
        assertThrows(IllegalStateException.class, () -> BattleSnapshots.restore(lobby));
    }

    @Test @DisplayName("forfeit ends the battle in the opponent's favour, once")
    void forfeit() {
        Battle b = battle(mon("A", PokemonType.NORMAL, 100, TACKLE), mon("B", PokemonType.NORMAL, 50, TACKLE));
        BattleEngine e = new BattleEngine(rules, new JavaRandomSource(1));
        List<BattleEvent> events = e.forfeit(b, Side.P1);
        assertEquals(BattleStatus.FINISHED, b.status());
        assertEquals(Side.P2, b.winner());
        assertEquals(1, count(events, EventType.BATTLE_END));
        assertThrows(IllegalActionException.class, () -> e.forfeit(b, Side.P2));
    }

    @Test @DisplayName("validate rejects an illegal action up front, without touching the battle")
    void validateIsPublic() {
        Battle b = battle(mon("A", PokemonType.NORMAL, 100, TACKLE), mon("B", PokemonType.NORMAL, 50, TACKLE));
        BattleEngine e = new BattleEngine(rules, new JavaRandomSource(1));
        assertThrows(IllegalActionException.class, () -> e.validate(b, Side.P1, move(3)));
        e.validate(b, Side.P1, move(0));
        assertEquals(0, b.turn());
    }
}

package com.pokesim.engine;

import java.util.List;

/** Small builders so each test shows only what it cares about. */
final class Fixtures {
    static final Move TACKLE = Move.damaging("Tackle", PokemonType.NORMAL, 40, 100, 35);
    static final Move QUICK_ATTACK = new Move("Quick Attack", PokemonType.NORMAL, 40, 100, 30, 1,
            StatusCondition.NONE, 0, 0);

    private Fixtures() {}

    /** A Pokemon with explicit stats: 200 HP and 100 in everything except speed. */
    static BattlePokemon mon(String name, PokemonType type, int speed, Move... moves) {
        return mon(name, type, 200, speed, moves);
    }

    static BattlePokemon mon(String name, PokemonType type, int hp, int speed, Move... moves) {
        Species species = new Species(1, name, List.of(type), new StatBlock(50, 50, 50, 50, 50, 50));
        return new BattlePokemon(species, 50, new StatBlock(hp, 100, 100, 100, 100, speed), List.of(moves));
    }

    static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(Side.P1, List.of(p1)), new BattleSide(Side.P2, List.of(p2)));
    }

    static Battle battle(List<BattlePokemon> p1Team, List<BattlePokemon> p2Team) {
        return new Battle(new BattleSide(Side.P1, p1Team), new BattleSide(Side.P2, p2Team));
    }

    static Action.UseMove move(int index) { return new Action.UseMove(index); }

    static List<Side> sidesOf(List<BattleEvent> events, EventType type) {
        return events.stream().filter(e -> e.type() == type).map(BattleEvent::side).toList();
    }

    static long count(List<BattleEvent> events, EventType type) {
        return events.stream().filter(e -> e.type() == type).count();
    }

    static BattleEvent first(List<BattleEvent> events, EventType type) {
        return events.stream().filter(e -> e.type() == type).findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " event in " + events));
    }
}

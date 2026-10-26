package com.pokesim.engine;

import java.util.List;

/**
 * Converts between the live, mutable {@link Battle} and the immutable {@link BattleSnapshot}.
 * Round-trip guarantee (tested): restore(capture(b)) behaves identically to b for every future turn.
 */
public final class BattleSnapshots {
    private BattleSnapshots() { }

    public static BattleSnapshot capture(Battle battle) {
        return new BattleSnapshot(captureSide(battle.side(Side.P1)), captureSide(battle.side(Side.P2)), battle.turn(),
                battle.status(), List.copyOf(battle.pendingReplacements()), battle.winner());
    }

    public static SideSnapshot captureSide(BattleSide side) {
        return new SideSnapshot(side.team().stream().map(BattleSnapshots::capture).toList(), side.activeIndex());
    }

    private static PokemonSnapshot capture(BattlePokemon p) {
        List<MoveState> moves = p.moves().stream().map(slot -> {
            Move m = slot.move();
            return new MoveState(m.name(), m.type(), m.power(), m.accuracy(), m.pp(), m.priority(), m.ailment(),
                    m.ailmentChance(), m.recoilDivisor(), slot.pp());
        }).toList();
        return new PokemonSnapshot(p.species().dexNumber(), p.name(), p.types(), p.species().baseStats(), p.level(),
                p.stats(), moves, p.currentHp(), p.status(), p.sleepTurns(), p.toxicCounter());
    }

    public static Battle restore(BattleSnapshot snapshot) {
        if (snapshot.p2() == null) throw new IllegalStateException("Cannot restore a battle that has no second player");
        Battle battle = new Battle(restoreSide(Side.P1, snapshot.p1()), restoreSide(Side.P2, snapshot.p2()));
        battle.restore(snapshot.turn(), snapshot.status(), snapshot.pendingReplacements(), snapshot.winner());
        return battle;
    }

    public static BattleSide restoreSide(Side id, SideSnapshot snapshot) {
        BattleSide side = new BattleSide(id, snapshot.team().stream().map(BattleSnapshots::restore).toList());
        side.setActive(snapshot.activeIndex());
        return side;
    }

    private static BattlePokemon restore(PokemonSnapshot s) {
        Species species = new Species(s.dexNumber(), s.name(), s.types(), s.baseStats());
        List<Move> moves = s.moves().stream().map(m -> new Move(m.name(), m.type(), m.power(), m.accuracy(), m.maxPp(),
                m.priority(), m.ailment(), m.ailmentChance(), m.recoilDivisor())).toList();
        BattlePokemon pokemon = new BattlePokemon(species, s.level(), s.stats(), moves);
        pokemon.restoreState(s.currentHp(), s.status(), s.sleepTurns(), s.toxicCounter());
        for (int i = 0; i < s.moves().size(); i++) pokemon.moves().get(i).setPp(s.moves().get(i).pp());
        return pokemon;
    }
}

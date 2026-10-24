package com.pokesim.engine;

import java.util.List;

/** One player's team (up to 6) and which member is currently out. */
public final class BattleSide {
    private final Side id;
    private final List<BattlePokemon> team;
    private int activeIndex;

    public BattleSide(Side id, List<BattlePokemon> team) {
        if (team.isEmpty() || team.size() > 6) throw new IllegalArgumentException("team must have 1-6 Pokemon");
        this.id = id;
        this.team = List.copyOf(team);
    }

    public Side id() { return id; }
    public List<BattlePokemon> team() { return team; }
    public int activeIndex() { return activeIndex; }
    public BattlePokemon active() { return team.get(activeIndex); }
    public void setActive(int index) { this.activeIndex = index; }
    public boolean hasUsablePokemon() { return team.stream().anyMatch(p -> !p.isFainted()); }
}

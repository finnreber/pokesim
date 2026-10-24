package com.pokesim.engine;

import java.util.List;

/** One Pokemon in a battle: species + level + computed stats + mutable battle state. */
public final class BattlePokemon {
    private final Species species;
    private final int level;
    private final StatBlock stats;
    private final List<MoveSlot> moves;
    private int currentHp;
    private StatusCondition status = StatusCondition.NONE;
    private int sleepTurns;
    private int toxicCounter;

    public BattlePokemon(Species species, int level, StatBlock stats, List<Move> moves) {
        if (level < 1 || level > 100) throw new IllegalArgumentException("level must be 1-100");
        if (moves.isEmpty() || moves.size() > 4) throw new IllegalArgumentException("1-4 moves required");
        this.species = species;
        this.level = level;
        this.stats = stats;
        this.moves = moves.stream().map(MoveSlot::new).toList();
        this.currentHp = stats.hp();
    }

    /** Normal way to build one: stats are derived from the species by the active ruleset. */
    public static BattlePokemon create(Species species, int level, List<Move> moves, Ruleset rules) {
        return new BattlePokemon(species, level, rules.computeStats(species.baseStats(), level), moves);
    }

    public String name() { return species.name(); }
    public List<PokemonType> types() { return species.types(); }
    public int level() { return level; }
    public StatBlock stats() { return stats; }
    public List<MoveSlot> moves() { return moves; }
    public int currentHp() { return currentHp; }
    public int maxHp() { return stats.hp(); }
    public boolean isFainted() { return currentHp <= 0; }
    public StatusCondition status() { return status; }
    public int sleepTurns() { return sleepTurns; }
    public int toxicCounter() { return toxicCounter; }
    public boolean hasAnyPp() { return moves.stream().anyMatch(m -> m.pp() > 0); }

    /** @return the HP actually removed (never more than the HP left) */
    public int takeDamage(int amount) {
        int dealt = Math.min(Math.max(amount, 0), currentHp);
        currentHp -= dealt;
        return dealt;
    }

    public void setStatus(StatusCondition status) {
        this.status = status;
        if (status != StatusCondition.BADLY_POISONED) toxicCounter = 0;
        if (status != StatusCondition.SLEEP) sleepTurns = 0;
    }

    public void setSleepTurns(int turns) { this.sleepTurns = turns; }
    public void decrementSleep() { if (sleepTurns > 0) sleepTurns--; }
    public void incrementToxic() { toxicCounter++; }
    public void resetToxic() { toxicCounter = 0; }
}

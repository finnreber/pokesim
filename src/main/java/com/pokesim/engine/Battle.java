package com.pokesim.engine;

import java.util.EnumSet;
import java.util.Set;

/** The complete, serializable state of one battle. The engine mutates it; it never stores anything else. */
public final class Battle {
    private final BattleSide p1;
    private final BattleSide p2;
    private int turn;
    private BattleStatus status = BattleStatus.AWAITING_ACTIONS;
    private final Set<Side> pendingReplacements = EnumSet.noneOf(Side.class);
    private Side winner;

    public Battle(BattleSide p1, BattleSide p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public BattleSide side(Side side) { return side == Side.P1 ? p1 : p2; }
    public int turn() { return turn; }
    public BattleStatus status() { return status; }
    public Set<Side> pendingReplacements() { return pendingReplacements; }
    /** Null while in progress, and also null for a draw (check status == FINISHED). */
    public Side winner() { return winner; }

    /** Used only when rebuilding a saved battle (see BattleSnapshots). */
    void restore(int turn, BattleStatus status, java.util.Collection<Side> pending, Side winner) {
        this.turn = turn;
        this.status = status;
        this.pendingReplacements.clear();
        this.pendingReplacements.addAll(pending);
        this.winner = winner;
    }

    void advanceTurn() { turn++; }
    void setStatus(BattleStatus status) { this.status = status; }
    void finish(Side winner) {
        this.status = BattleStatus.FINISHED;
        this.winner = winner;
        this.pendingReplacements.clear();
    }
}

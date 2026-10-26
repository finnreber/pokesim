package com.pokesim.persistence;

import com.pokesim.engine.BattleSnapshot;
import com.pokesim.engine.Side;

/** What is stored in the JSONB column: the engine snapshot plus any action waiting for the other player. */
public record BattleState(BattleSnapshot battle, PendingAction p1Action, PendingAction p2Action) {

    public PendingAction pending(Side side) {
        return side == Side.P1 ? p1Action : p2Action;
    }

    public BattleState withPending(Side side, PendingAction action) {
        return side == Side.P1 ? new BattleState(battle, action, p2Action) : new BattleState(battle, p1Action, action);
    }
}

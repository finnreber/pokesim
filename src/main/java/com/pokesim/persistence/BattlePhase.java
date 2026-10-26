package com.pokesim.persistence;

import com.pokesim.engine.BattleStatus;

/** Engine statuses plus the one phase that exists before the engine does: a lobby waiting for an opponent. */
public enum BattlePhase {
    WAITING_FOR_OPPONENT, AWAITING_ACTIONS, AWAITING_REPLACEMENT, FINISHED;

    public static BattlePhase from(BattleStatus status) {
        return switch (status) {
            case AWAITING_ACTIONS -> AWAITING_ACTIONS;
            case AWAITING_REPLACEMENT -> AWAITING_REPLACEMENT;
            case FINISHED -> FINISHED;
        };
    }
}

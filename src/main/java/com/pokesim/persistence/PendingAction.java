package com.pokesim.persistence;

import com.pokesim.engine.Action;
import com.pokesim.engine.ActionKind;

/** A choice one player has made this turn and the opponent has not yet answered. Stored with the battle. */
public record PendingAction(ActionKind kind, int index) {
    public Action toAction() {
        return Action.of(kind, index);
    }
}

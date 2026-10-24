package com.pokesim.engine;

/** What a player can submit each turn. Sealed: the compiler knows every possible action. */
public sealed interface Action permits Action.UseMove, Action.Switch {
    record UseMove(int moveIndex) implements Action {}
    record Switch(int teamIndex) implements Action {}
}

package com.pokesim.engine;

import java.util.List;

/** The complete state of a battle as plain data (safe to store as JSON). {@code p2} is null while a lobby waits. */
public record BattleSnapshot(SideSnapshot p1, SideSnapshot p2, int turn, BattleStatus status,
                             List<Side> pendingReplacements, Side winner) { }

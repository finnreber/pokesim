package com.pokesim.api.dto;

import java.util.UUID;

/** One player's view of a battle. {@code winner} is YOU, OPPONENT or DRAW once finished; {@code opponent} is null in a lobby. */
public record BattleView(UUID id, String phase, int turn, String youAre, boolean awaitingYourAction, String winner,
                         SideView you, SideView opponent) { }

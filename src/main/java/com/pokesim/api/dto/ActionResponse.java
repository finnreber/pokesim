package com.pokesim.api.dto;

import java.util.List;

/** The updated battle, plus any events this submission caused (empty if the opponent has not answered yet). */
public record ActionResponse(BattleView battle, List<EventDto> events, boolean waitingForOpponent) { }

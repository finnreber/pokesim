package com.pokesim.api.dto;

import java.util.List;

public record SimulationResult(long seed, int turns, String winner, List<String> p1Team, List<String> p2Team,
                               List<EventDto> events) { }

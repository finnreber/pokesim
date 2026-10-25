package com.pokesim.api.dto;

import java.util.List;

public record SpeciesDetail(int dexNumber, String name, int generation, List<String> types, BaseStatsDto baseStats) { }

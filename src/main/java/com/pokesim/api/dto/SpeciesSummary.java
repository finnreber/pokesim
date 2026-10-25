package com.pokesim.api.dto;

import java.util.List;

public record SpeciesSummary(int dexNumber, String name, List<String> types) { }

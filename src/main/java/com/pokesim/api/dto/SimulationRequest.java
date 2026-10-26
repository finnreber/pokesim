package com.pokesim.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Both fields optional. The same seed and team size always produce the same battle. */
public record SimulationRequest(Long seed, @Min(1) @Max(6) Integer teamSize) { }

package com.pokesim.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MemberRequest(
        @Min(1) int dexNumber,
        @Min(1) @Max(100) int level,
        @NotNull @Size(min = 1, max = 4) List<@NotNull Integer> moveIds) { }
